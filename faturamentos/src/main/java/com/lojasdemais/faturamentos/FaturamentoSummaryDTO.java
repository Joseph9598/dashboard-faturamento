package com.lojasdemais.faturamentos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaturamentoSummaryDTO {
    private BigDecimal faturamentoTotal;
    private Long quantidadeNotas;
    private BigDecimal ticketMedio;
    private BigDecimal totalIcms;
    private List<LojaSummaryDTO> lojas; // Lista consolidada apenas com o total por loja
}