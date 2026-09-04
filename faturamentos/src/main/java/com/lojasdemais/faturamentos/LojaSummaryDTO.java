package com.lojasdemais.faturamentos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LojaSummaryDTO {
    private String nomeLoja;
    private String cnpj;
    private Long quantidadeNotas;
    private BigDecimal faturamentoTotal;
    private BigDecimal ticketMedio;
    private BigDecimal totalIcms;
}