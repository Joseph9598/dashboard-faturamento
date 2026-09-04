package com.lojasdemais.faturamentos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;

@Service
public class TinyLojaService {

    @Value("${tiny.loja1.token:}")
    private String tokenLissModa;

    @Value("${tiny.loja2.token:}")
    private String tokenMcl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public void buscarEAnexarLojasTiny(FaturamentoSummaryDTO summary, String dataInicio, String dataFim) {
        if (summary == null) return;

        if (summary.getLojas() == null) {
            summary.setLojas(new ArrayList<>());
        } else {
            try {
                summary.setLojas(new ArrayList<>(summary.getLojas()));
            } catch (UnsupportedOperationException e) {
                summary.setLojas(new ArrayList<>());
            }
        }

        if (tokenLissModa != null && !tokenLissModa.isBlank()) {
            LojaSummaryDTO lissModa = processarLojaTiny(tokenLissModa, "LISS MODA", dataInicio, dataFim);
            if (lissModa != null) {
                summary.getLojas().add(lissModa);
            }
        }

        if (tokenMcl != null && !tokenMcl.isBlank()) {
            LojaSummaryDTO mcl = processarLojaTiny(tokenMcl, "MCL", dataInicio, dataFim);
            if (mcl != null) {
                summary.getLojas().add(mcl);
            }
        }
    }

    private LojaSummaryDTO processarLojaTiny(String token, String nomeLoja, String dataInicio, String dataFim) {
        try {
            URI uriPesquisa = UriComponentsBuilder.fromHttpUrl("https://api.tiny.com.br/api2/notas.fiscais.pesquisa.php")
                    .queryParam("token", token)
                    .queryParam("dataInicial", formatarDataParaTiny(dataInicio))
                    .queryParam("dataFinal", formatarDataParaTiny(dataFim))
                    .queryParam("format", "json")
                    .build()
                    .toUri();

            String responseBody = restTemplate.getForObject(uriPesquisa, String.class);

            // Extrai IDs das notas diretamente da resposta (suporta JSON ou XML)
            ArrayList<String> idsNotas = extrairIdsDasNotas(responseBody);

            if (idsNotas.isEmpty()) {
                return criarLojaVazia(nomeLoja);
            }

            BigDecimal faturamentoTotal = BigDecimal.ZERO;
            BigDecimal totalIcms = BigDecimal.ZERO;
            int quantidadeNotas = 0;

            for (String idNota : idsNotas) {
                DetailNota detail = obterDetalhesNota(token, idNota);
                if (detail != null) {
                    faturamentoTotal = faturamentoTotal.add(detail.valorTotal);
                    totalIcms = totalIcms.add(detail.valorIcms);
                    quantidadeNotas++;
                }
            }

            BigDecimal ticketMedio = quantidadeNotas > 0
                    ? faturamentoTotal.divide(new BigDecimal(quantidadeNotas), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            LojaSummaryDTO dto = new LojaSummaryDTO();
            dto.setNomeLoja(nomeLoja);
            dto.setFaturamentoTotal(faturamentoTotal);
            dto.setTotalIcms(totalIcms);
            dto.setQuantidadeNotas((long) quantidadeNotas);
            dto.setTicketMedio(ticketMedio);

            return dto;

        } catch (Exception e) {
            e.printStackTrace();
            return criarLojaVazia(nomeLoja);
        }
    }

    private ArrayList<String> extrairIdsDasNotas(String responseBody) {
        ArrayList<String> ids = new ArrayList<>();
        if (responseBody == null || responseBody.isBlank()) return ids;

        String trimmed = responseBody.trim();

        try {
            // Caso 1: Se o Tiny retornar XML nativo
            if (trimmed.startsWith("<")) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(trimmed)));

                NodeList idNodes = doc.getElementsByTagName("id");
                for (int i = 0; i < idNodes.getLength(); i++) {
                    String id = idNodes.item(i).getTextContent();
                    if (id != null && !id.isBlank() && !ids.contains(id)) {
                        ids.add(id);
                    }
                }
                return ids;
            }

            // Caso 2: Se o Tiny retornar JSON nativo
            JsonNode root = jsonMapper.readTree(trimmed);
            JsonNode retorno = root.path("retorno");

            if (!"OK".equalsIgnoreCase(retorno.path("status").asText())) {
                return ids;
            }

            JsonNode notasNode = retorno.path("notas_fiscais").path("nota_fiscal");
            if (notasNode.isMissingNode() || notasNode.isNull()) {
                notasNode = retorno.path("notas").path("nota");
            }

            if (notasNode.isArray()) {
                for (JsonNode item : notasNode) {
                    String id = extrairIdNotaJson(item);
                    if (!id.isBlank()) ids.add(id);
                }
            } else if (notasNode.isObject()) {
                String id = extrairIdNotaJson(notasNode);
                if (!id.isBlank()) ids.add(id);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ids;
    }

    private DetailNota obterDetalhesNota(String token, String idNota) {
        try {
            URI uriObter = UriComponentsBuilder.fromHttpUrl("https://api.tiny.com.br/api2/nota.fiscal.obter.php")
                    .queryParam("token", token)
                    .queryParam("id", idNota)
                    .queryParam("format", "json")
                    .build()
                    .toUri();

            String responseBody = restTemplate.getForObject(uriObter, String.class);
            if (responseBody == null) return new DetailNota(BigDecimal.ZERO, BigDecimal.ZERO);

            String trimmed = responseBody.trim();

            if (trimmed.startsWith("<")) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(trimmed)));

                NodeList vNota = doc.getElementsByTagName("valor_nota");
                NodeList vIcms = doc.getElementsByTagName("valor_icms");

                BigDecimal valorTotal = vNota.getLength() > 0 ? new BigDecimal(vNota.item(0).getTextContent()) : BigDecimal.ZERO;
                BigDecimal valorIcms = vIcms.getLength() > 0 ? new BigDecimal(vIcms.item(0).getTextContent()) : BigDecimal.ZERO;

                return new DetailNota(valorTotal, valorIcms);
            }

            JsonNode root = jsonMapper.readTree(trimmed);
            JsonNode nota = root.path("retorno").path("nota");

            BigDecimal valorTotal = new BigDecimal(nota.path("valor_nota").asText("0.00"));
            BigDecimal valorIcms = new BigDecimal(nota.path("valor_icms").asText("0.00"));

            return new DetailNota(valorTotal, valorIcms);
        } catch (Exception e) {
            return new DetailNota(BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private String extrairIdNotaJson(JsonNode itemNota) {
        if (itemNota.has("id")) {
            return itemNota.path("id").asText();
        }
        return itemNota.path("nota").path("id").asText("");
    }

    private LojaSummaryDTO criarLojaVazia(String nomeLoja) {
        LojaSummaryDTO dto = new LojaSummaryDTO();
        dto.setNomeLoja(nomeLoja);
        dto.setFaturamentoTotal(BigDecimal.ZERO);
        dto.setTotalIcms(BigDecimal.ZERO);
        dto.setQuantidadeNotas(0L);
        dto.setTicketMedio(BigDecimal.ZERO);
        return dto;
    }

    private String formatarDataParaTiny(String data) {
        if (data == null || data.isBlank()) return "";
        if (data.contains("-")) {
            String[] partes = data.split("-");
            if (partes.length == 3) {
                return partes[2] + "/" + partes[1] + "/" + partes[0];
            }
        }
        return data;
    }

    private static class DetailNota {
        BigDecimal valorTotal;
        BigDecimal valorIcms;

        DetailNota(BigDecimal valorTotal, BigDecimal valorIcms) {
            this.valorTotal = valorTotal;
            this.valorIcms = valorIcms;
        }
    }
}