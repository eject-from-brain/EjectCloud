package org.ejectfb.ejectcloud.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPanelController {
    
    @GetMapping("/admin-panel")
    public String adminPanel() {
        return "redirect:/admin-panel.html";
    }
}