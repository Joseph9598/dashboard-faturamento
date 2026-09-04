package com.lojasdemais.faturamentos;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final NfeXmlService xmlService;
    private final FaturamentoService faturamentoService;
    private final TinyLojaService tinyLojaService; // Injeção do serviço do Tiny

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        @SuppressWarnings("unchecked")
        List<NfeDaDTO> notas = (List<NfeDaDTO>) session.getAttribute("notasProcessadas");
        LocalDate dataInicio = (LocalDate) session.getAttribute("dataInicio");
        LocalDate dataFim = (LocalDate) session.getAttribute("dataFim");

        if (notas == null) {
            notas = List.of();
        }

        FaturamentoSummaryDTO summary = faturamentoService.calcularResumoFaturamento(notas, dataInicio, dataFim);

        // Adiciona as lojas do Tiny (LISS MODA e MCL) ao resumo
        anexarLojasTiny(summary, dataInicio, dataFim);

        model.addAttribute("summary", summary);
        model.addAttribute("dataInicio", dataInicio);
        model.addAttribute("dataFim", dataFim);
        return "dashboard";
    }

    // Processa novos arquivos XML (Upload Inicial)
    @PostMapping("/upload")
    public String processarUpload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            Model model,
            HttpSession session) {
        try {
            List<NfeDaDTO> notas = xmlService.processarXmls(files);

            // Grava os dados na sessão
            session.setAttribute("notasProcessadas", notas);
            session.setAttribute("dataInicio", dataInicio);
            session.setAttribute("dataFim", dataFim);

            FaturamentoSummaryDTO summary = faturamentoService.calcularResumoFaturamento(notas, dataInicio, dataFim);

            // Adiciona as lojas do Tiny ao resumo
            anexarLojasTiny(summary, dataInicio, dataFim);

            model.addAttribute("summary", summary);
            model.addAttribute("dataInicio", dataInicio);
            model.addAttribute("dataFim", dataFim);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("errorMessage", "Erro ao processar os arquivos XML: " + e.getMessage());

            FaturamentoSummaryDTO summaryVazio = faturamentoService.calcularResumoFaturamento(List.of(), null, null);
            anexarLojasTiny(summaryVazio, dataInicio, dataFim);
            model.addAttribute("summary", summaryVazio);
        }
        return "dashboard";
    }

    // Filtra instantaneamente as notas JÁ PROCESSADAS na sessão
    @GetMapping("/filtrar")
    public String filtrarDatasEmMemoria(
            @RequestParam(value = "dataInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(value = "dataFim", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            Model model,
            HttpSession session) {

        @SuppressWarnings("unchecked")
        List<NfeDaDTO> notasEmMemoria = (List<NfeDaDTO>) session.getAttribute("notasProcessadas");

        // Atualiza o período filtrado na sessão
        session.setAttribute("dataInicio", dataInicio);
        session.setAttribute("dataFim", dataFim);

        List<NfeDaDTO> notasParaCalcular = (notasEmMemoria != null) ? notasEmMemoria : List.of();

        // Recalcula os totais e lojas com as novas datas
        FaturamentoSummaryDTO summary = faturamentoService.calcularResumoFaturamento(notasParaCalcular, dataInicio, dataFim);

        // Adiciona as lojas do Tiny ao resumo
        anexarLojasTiny(summary, dataInicio, dataFim);

        model.addAttribute("summary", summary);
        model.addAttribute("dataInicio", dataInicio);
        model.addAttribute("dataFim", dataFim);

        return "dashboard";
    }

    // Endpoint assíncrono mantido para renderização das notas da loja
    @RequestMapping(value = "/api/loja/notas", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public List<NfeDaDTO> getNotasPorLoja(@RequestParam("nomeLoja") String nomeLoja, HttpSession session) {
        @SuppressWarnings("unchecked")
        List<NfeDaDTO> todasNotas = (List<NfeDaDTO>) session.getAttribute("notasProcessadas");
        LocalDate dataInicio = (LocalDate) session.getAttribute("dataInicio");
        LocalDate dataFim = (LocalDate) session.getAttribute("dataFim");

        if (todasNotas == null) return List.of();

        List<NfeDaDTO> notasFiltradas = faturamentoService.filtrarPorData(todasNotas, dataInicio, dataFim);

        return notasFiltradas.stream()
                .filter(n -> nomeLoja.equalsIgnoreCase(n.getEmitenteRazaoSocial()))
                .collect(Collectors.toList());
    }

    // Método utilitário para converter LocalDate -> String e chamar o TinyLojaService
    private void anexarLojasTiny(FaturamentoSummaryDTO summary, LocalDate dataInicio, LocalDate dataFim) {
        String inicioStr = (dataInicio != null) ? dataInicio.toString() : "";
        String fimStr = (dataFim != null) ? dataFim.toString() : "";
        tinyLojaService.buscarEAnexarLojasTiny(summary, inicioStr, fimStr);
    }
}