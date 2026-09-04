package com.lojasdemais.faturamentos;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class NfeXmlService {

    public List<NfeDaDTO> processarXmls(List<MultipartFile> files) {
        List<NfeDaDTO> notas = new ArrayList<>();

        if (files == null || files.isEmpty()) {
            return notas;
        }

        for (MultipartFile file : files) {
            if (!file.isEmpty() && file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".xml")) {
                try (InputStream is = file.getInputStream()) {
                    NfeDaDTO nfe = parseXmlInputStream(is);
                    if (nfe != null) {
                        notas.add(nfe);
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao processar arquivo " + file.getOriginalFilename() + ": " + e.getMessage());
                }
            }
        }
        return notas;
    }

    private NfeDaDTO parseXmlInputStream(InputStream inputStream) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(false);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        NodeList infNfeList = doc.getElementsByTagName("infNFe");
        if (infNfeList.getLength() == 0) return null;
        Element infNfe = (Element) infNfeList.item(0);

        String chave = infNfe.getAttribute("Id");
        if (chave != null && chave.startsWith("NFe")) {
            chave = chave.substring(3);
        }

        String nNF = getTagValue("nNF", infNfe);
        String dhEmiStr = getTagValue("dhEmi", infNfe);
        if (dhEmiStr.isEmpty()) {
            dhEmiStr = getTagValue("dEmi", infNfe);
        }

        LocalDateTime dhEmi = null;
        if (!dhEmiStr.isEmpty()) {
            try {
                if (dhEmiStr.length() == 10) {
                    dhEmi = java.time.LocalDate.parse(dhEmiStr).atStartOfDay();
                } else {
                    dhEmi = LocalDateTime.parse(dhEmiStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                }
            } catch (Exception ignored) {}
        }

        // Dados do Emitente (Loja)
        NodeList emitList = doc.getElementsByTagName("emit");
        String emitNome = "Não informado";
        String emitCnpj = "";
        if (emitList.getLength() > 0) {
            Element emit = (Element) emitList.item(0);
            String xNome = getTagValue("xNome", emit);
            if (!xNome.isEmpty()) emitNome = xNome;
            emitCnpj = getTagValue("CNPJ", emit);
        }

        // Dados do Destinatário
        NodeList destList = doc.getElementsByTagName("dest");
        String destNome = destList.getLength() > 0 ? getTagValue("xNome", (Element) destList.item(0)) : "Consumidor Final";

        // Totais
        String vNFStr = getTagValue("vNF", doc.getDocumentElement());
        BigDecimal vNF = !vNFStr.isEmpty() ? new BigDecimal(vNFStr) : BigDecimal.ZERO;

        String vICMSStr = getTagValue("vICMS", doc.getDocumentElement());
        BigDecimal vICMS = !vICMSStr.isEmpty() ? new BigDecimal(vICMSStr) : BigDecimal.ZERO;

        return NfeDaDTO.builder()
                .chaveAcesso(chave)
                .numeroNota(nNF)
                .emitenteRazaoSocial(emitNome)
                .emitenteCnpj(emitCnpj)
                .destinatarioNome(destNome)
                .dataEmissao(dhEmi)
                .valorTotalNfe(vNF)
                .valorIcms(vICMS)
                .build();
    }

    private String getTagValue(String tag, Element element) {
        if (element == null) return "";
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }
}