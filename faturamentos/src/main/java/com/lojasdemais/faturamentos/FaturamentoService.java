package com.lojasdemais.faturamentos;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FaturamentoService {

    public FaturamentoSummaryDTO calcularResumoFaturamento(List<NfeDaDTO> notas, LocalDate dataInicio, LocalDate dataFim) {
        if (notas == null || notas.isEmpty()) {
            return FaturamentoSummaryDTO.builder()
                    .faturamentoTotal(BigDecimal.ZERO)
                    .quantidadeNotas(0L)
                    .ticketMedio(BigDecimal.ZERO)
                    .totalIcms(BigDecimal.ZERO)
                    .lojas(List.of())
                    .build();
        }

        List<NfeDaDTO> notasFiltradas = filtrarPorData(notas, dataInicio, dataFim);

        BigDecimal faturamentoTotal = notasFiltradas.stream()
                .map(NfeDaDTO::getValorTotalNfe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIcms = notasFiltradas.stream()
                .map(NfeDaDTO::getValorIcms)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long count = notasFiltradas.size();
        BigDecimal ticketMedio = count > 0
                ? faturamentoTotal.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Agrupa por CNPJ do emitente para garantir a individualidade de cada loja
        Map<String, List<NfeDaDTO>> notasPorCnpj = notasFiltradas.stream()
                .filter(nota -> nota.getEmitenteCnpj() != null && !nota.getEmitenteCnpj().isBlank())
                .collect(Collectors.groupingBy(NfeDaDTO::getEmitenteCnpj));

        List<LojaSummaryDTO> lojasSummary = new ArrayList<>();

        notasPorCnpj.forEach((cnpj, listaNotas) -> {
            BigDecimal totalLoja = listaNotas.stream()
                    .map(NfeDaDTO::getValorTotalNfe)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long qtdNotasLoja = listaNotas.size();

            BigDecimal ticketMedioLoja = qtdNotasLoja > 0
                    ? totalLoja.divide(BigDecimal.valueOf(qtdNotasLoja), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Busca a razão social do primeiro registro daquela loja
            String nomeLoja = listaNotas.stream()
                    .map(NfeDaDTO::getEmitenteRazaoSocial)
                    .filter(r -> r != null && !r.isBlank())
                    .findFirst()
                    .orElse("Loja sem Nome");

            lojasSummary.add(LojaSummaryDTO.builder()
                    .nomeLoja(nomeLoja)
                    .cnpj(cnpj)
                    .quantidadeNotas(qtdNotasLoja)
                    .faturamentoTotal(totalLoja)
                    .ticketMedio(ticketMedioLoja)
                    .build());
        });

        return FaturamentoSummaryDTO.builder()
                .faturamentoTotal(faturamentoTotal)
                .quantidadeNotas(count)
                .ticketMedio(ticketMedio)
                .totalIcms(totalIcms)
                .lojas(lojasSummary)
                .build();
    }

    public List<NfeDaDTO> filtrarPorData(List<NfeDaDTO> notas, LocalDate dataInicio, LocalDate dataFim) {
        if (notas == null) return List.of();
        return notas.stream()
                .filter(nota -> {
                    if (nota.getDataEmissao() == null) return true;
                    LocalDate dataNota = nota.getDataEmissao().toLocalDate();
                    boolean aposOuIgualInicio = (dataInicio == null) || !dataNota.isBefore(dataInicio);
                    boolean antesOuIgualFim = (dataFim == null) || !dataNota.isAfter(dataFim);
                    return aposOuIgualInicio && antesOuIgualFim;
                })
                .collect(Collectors.toList());
    }
}