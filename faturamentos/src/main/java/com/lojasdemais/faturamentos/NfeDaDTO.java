package com.lojasdemais.faturamentos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class NfeDaDTO {
    private String chaveAcesso;
    private String numeroNota;
    private String emitenteRazaoSocial;
    private String emitenteCnpj;
    private String destinatarioNome;
    private LocalDateTime dataEmissao;
    private BigDecimal valorTotalNfe;
    private BigDecimal valorIcms;
}