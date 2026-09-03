package com.cinebuscador.controller;

import com.cinebuscador.config.SpelEvaluator;
import com.cinebuscador.model.Funcion;
import com.cinebuscador.repository.FuncionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class FuncionController {

    private final FuncionRepository funcionRepo;
    private final SpelEvaluator spelEval;

    @Autowired
    public FuncionController(FuncionRepository funcionRepo, SpelEvaluator spelEval) {
        this.funcionRepo = funcionRepo;
        this.spelEval = spelEval;
    }

    @GetMapping("/")
    public String search(@RequestParam(required = false) String buscar, Model model) {

        model.addAttribute("query", buscar != null ? buscar : "");

        if (buscar == null || buscar.isBlank()) {
            System.out.println("buscar es: " + buscar);
            // Mostrar todas las funciones si no hay busqueda
            List<Funcion> todas = funcionRepo.findAll();
            model.addAttribute("resultados", todas);
            model.addAttribute("mensaje", "Mostrando todas las funciones.");
            return "index";
        }

        String spelResultado = spelEval.evaluate(buscar);

        model.addAttribute("spelOutput", spelResultado);

        if (!spelResultado.isBlank()) {
            List<Funcion> resultados = funcionRepo.findAll().stream()
                .filter(f -> f.getNombreFuncion() != null &&
                             f.getNombreFuncion().toLowerCase().contains(spelResultado.toLowerCase()))
                .collect(Collectors.toList());
            model.addAttribute("resultados", resultados);
            model.addAttribute("mensaje", "Resultados buscando por: " + spelResultado);
        } else {
            model.addAttribute("resultados", new ArrayList<Funcion>());
            model.addAttribute("mensaje", "No se encontraron coincidencias.");
        }

        return "index";
    }
}
