package com.cinebuscador.controller;

import com.cinebuscador.config.EncryptionService;
import com.cinebuscador.repository.UserRepository;
import com.cinebuscador.model.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ==================== LOGIN PAGE ====================
    @GetMapping("/")
    public String loginPage(Model model) {
        model.addAttribute("loginForm", new LoginForm());
        model.addAttribute("registerForm", new RegisterForm());
        return "index";
    }

    // ==================== LOGIN ====================
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, Model model) {
        User user = userRepository.findByUsername(username).orElse(null);

        if (user != null && EncryptionService.matches(password, user.getPassword())) {
            model.addAttribute("loginSuccess", true);
            model.addAttribute("welcomeUser", username);
            return "index";
        }
        model.addAttribute("loginError", "Usuario o contraseña incorrecta");
        addForms(model);
        return "index";
    }

    // ==================== REGISTER ====================
    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password,
                           @RequestParam String confirmPwd, Model model) {
        if (!password.equals(confirmPwd)) {
            model.addAttribute("registerError", "Las contraseñas no coinciden");
            addForms(model);
            return "index";
        }

        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("registerError", "El usuario ya existe");
            addForms(model);
            return "index";
        }


        com.cinebuscador.model.User nuevoUsuario = new com.cinebuscador.model.User();
        nuevoUsuario.setUsername(username);
        nuevoUsuario.setPassword(EncryptionService.encrypt(password));
        userRepository.save(nuevoUsuario);

        model.addAttribute("registerSuccess", true);
        model.addAttribute("registeredUsername", username);
        addForms(model);
        return "index";
    }

    // ==================== FORM BEANS ====================
    public static class LoginForm {
        private String username, password;
        public String getUsername() { return username; }
        public void setUsername(String u) { username = u; }
        public String getPassword() { return password; }
        public void setPassword(String p) { password = p; }
    }

    public static class RegisterForm {
        private String username, password, confirmPwd;
        public String getUsername() { return username; }
        public void setUsername(String u) { username = u; }
        public String getPassword() { return password; }
        public void setPassword(String p) { password = p; }
        public String getConfirmPwd() { return confirmPwd; }
        public void setConfirmPwd(String c) { confirmPwd = c; }
    }

    private void addForms(Model model) {
        model.addAttribute("loginForm", new LoginForm());
        model.addAttribute("registerForm", new RegisterForm());
    }
}
