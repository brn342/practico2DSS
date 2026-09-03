package com.cinebuscador.controller;

import com.cinebuscador.model.Funcion;
import com.cinebuscador.repository.FuncionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class FuncionController {

    private final FuncionRepository funcionRepo;

    @Autowired
    public FuncionController(FuncionRepository funcionRepo) {
        this.funcionRepo = funcionRepo;
    }

    @GetMapping("/")
    public String search(@RequestParam(required = false) String buscar, Model model) {

        model.addAttribute("query", buscar != null ? buscar : "");

        if (buscar == null || buscar.isBlank()) {
            List<Funcion> todas = funcionRepo.findAll();
            model.addAttribute("resultados", todas);
            model.addAttribute("mensaje", "Mostrando todas las funciones.");
            return "index";
        }

        // Mitigacion SSTI / SpEL injection (CWE-94 / CWE-917):
        // el termino de busqueda se usa como DATO literal (comparacion de texto),
        // nunca se interpreta como expresion/plantilla. Se elimino SpelEvaluator.
        String termino = buscar.trim().toLowerCase();

        List<Funcion> resultados = funcionRepo.findAll().stream()
            .filter(f -> f.getNombreFuncion() != null &&
                         f.getNombreFuncion().toLowerCase().contains(termino))
            .collect(Collectors.toList());

        model.addAttribute("resultados", resultados);
        model.addAttribute("mensaje", resultados.isEmpty()
            ? "No se encontraron coincidencias."
            : "Resultados buscando por: " + buscar.trim());

        return "index";
    }
}
