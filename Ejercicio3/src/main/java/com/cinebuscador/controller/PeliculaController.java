package com.cinebuscador.controller;

import com.cinebuscador.model.Pelicula;
import com.cinebuscador.repository.PeliculaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.core.io.UrlResource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class PeliculaController {

    private final PeliculaRepository peliculaRepo;

    @Value("${app.upload-dir}")
    private String uploadDir;

    // ---- Mitigacion File Upload (CWE-434) / Path Traversal (CWE-22) ----
    private static final long MAX_BYTES = 2L * 1024 * 1024; // 2 MB

    // Allowlist por FORMATO REAL de imagen (lo que devuelve ImageIO), no por
    // extension ni por el Content-Type que manda el cliente.
    private static final Map<String, String> FORMATO_A_EXT = Map.of(
        "JPEG", "jpg",
        "PNG",  "png",
        "GIF",  "gif",
        "BMP",  "bmp"
    );
    // Content-Type fijo y seguro con el que se sirve cada extension almacenada.
    private static final Map<String, MediaType> EXT_A_MEDIATYPE = Map.of(
        "jpg", MediaType.IMAGE_JPEG,
        "png", MediaType.IMAGE_PNG,
        "gif", MediaType.IMAGE_GIF,
        "bmp", MediaType.parseMediaType("image/bmp")
    );

    public PeliculaController(PeliculaRepository peliculaRepo) {
        this.peliculaRepo = peliculaRepo;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String buscar,
                        @RequestParam(required = false, defaultValue = "nombre") String ordenarPor,
                        @RequestParam(required = false, defaultValue = "ASC") String sentido,
                        Model model) {

        if (buscar != null && !buscar.isBlank()) {
            List<Object[]> resultadosRaw;
            if ("DESC".equalsIgnoreCase(sentido)) {
                resultadosRaw = peliculaRepo.searchWithFuncionesDesc(buscar, ordenarPor);
            } else {
                resultadosRaw = peliculaRepo.searchWithFunciones(buscar, ordenarPor);
            }

            // Wrap Object[] in Maps for cleaner Thymeleaf access
            List<Map<String, Object>> resultados = new java.util.ArrayList<>();
            for (Object[] row : resultadosRaw) {
                Map<String, Object> map = new HashMap<>();
                map.put("id",        row[0]);
                map.put("nombre",    row[1]);
                map.put("fechaHora", row[2]);
                map.put("disponibles", row[3]);
                map.put("descripcion", row[4]);
                map.put("afichePath", row[5]);
                resultados.add(map);
            }
            model.addAttribute("resultados", resultados);
        }

        model.addAttribute("query", buscar != null ? buscar : "");
        model.addAttribute("sort_by", ordenarPor);
        model.addAttribute("sort_dir", sentido);
        return "index";
    }

    @GetMapping("/upload/{id}")
    public String uploadForm(@PathVariable Integer id, Model model) {
        Pelicula pelicula = peliculaRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pelicula no encontrada"));
        model.addAttribute("pelicula", pelicula);
        return "upload";
    }

    @PostMapping("/upload/{id}")
    public String uploadFile(@PathVariable Integer id,
                             @RequestParam("afiche") MultipartFile archivo,
                             RedirectAttributes ra) throws IOException {
        Pelicula pelicula = peliculaRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Pelicula no encontrada"));

        // 1. Validaciones de tamano
        if (archivo == null || archivo.isEmpty()) {
            ra.addFlashAttribute("error", "El archivo esta vacio.");
            return "redirect:/upload/" + id;
        }
        if (archivo.getSize() > MAX_BYTES) {
            ra.addFlashAttribute("error", "El archivo supera el maximo permitido (2 MB).");
            return "redirect:/upload/" + id;
        }

        byte[] bytes = archivo.getBytes();

        // 2. Validacion por CONTENIDO: tiene que decodificar como imagen y su
        //    formato real debe estar en la allowlist. Esto rechaza HTML, SVG,
        //    JS, ejecutables y poliglotas.
        String ext = detectarExtensionImagen(bytes);
        if (ext == null) {
            ra.addFlashAttribute("error", "Formato no permitido. Solo JPG, PNG, GIF o BMP.");
            return "redirect:/upload/" + id;
        }

        // 3. Nombre generado en el servidor: el nombre del cliente se IGNORA por
        //    completo, por lo que no hay path traversal en la escritura.
        String safeName = UUID.randomUUID() + "." + ext;

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        Path destino = uploadPath.resolve(safeName).normalize();
        if (!destino.startsWith(uploadPath)) { // defensa en profundidad
            ra.addFlashAttribute("error", "Ruta de destino invalida.");
            return "redirect:/upload/" + id;
        }
        Files.write(destino, bytes);

        pelicula.setAfichePath(safeName);
        peliculaRepo.save(pelicula);

        return "redirect:/";
    }

    @GetMapping("/uploads/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws IOException {
        // 1. El nombre no puede contener separadores ni secuencias de traversal.
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        // 2. Solo se sirven extensiones de imagen conocidas, con Content-Type fijo.
        String ext = filename.contains(".")
            ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
            : "";
        MediaType mediaType = EXT_A_MEDIATYPE.get(ext);
        if (mediaType == null) {
            return ResponseEntity.notFound().build();
        }
        // 3. Contencion: la ruta resuelta tiene que quedar dentro de uploadDir.
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = base.resolve(filename).normalize();
        if (!filePath.startsWith(base)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
            .body(resource);
    }

    /**
     * Devuelve la extension segura ("jpg"/"png"/"gif"/"bmp") si los bytes
     * corresponden a una imagen de un formato de la allowlist; null en cualquier
     * otro caso (no es imagen, formato no permitido, corrupta).
     */
    private static String detectarExtensionImagen(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                String formato = reader.getFormatName().toUpperCase();
                String ext = FORMATO_A_EXT.get(formato);
                if (ext == null) {
                    return null;
                }
                reader.setInput(iis);
                // Fuerza la decodificacion real: si no es una imagen valida, lanza.
                reader.read(0);
                return ext;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
