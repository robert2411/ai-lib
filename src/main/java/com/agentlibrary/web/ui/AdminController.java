package com.agentlibrary.web.ui;

import com.agentlibrary.auth.UsersFile;
import com.agentlibrary.index.IndexService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin page controller for system administration tasks.
 * Requires ADMIN role.
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final IndexService indexService;
    private final UsersFile usersFile;

    public AdminController(IndexService indexService, UsersFile usersFile) {
        this.indexService = indexService;
        this.usersFile = usersFile;
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("users", usersFile.loadAll());
        model.addAttribute("indexCount", indexService.getAll().size());
        return "admin";
    }

    @PostMapping("/admin/reindex")
    public String reindex(Model model, HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        try {
            indexService.refresh();
            int count = indexService.getAll().size();
            String message = "Index rebuilt successfully — " + count + " artifacts";

            if (request.getHeader("HX-Request") != null) {
                model.addAttribute("toastMessage", message);
                model.addAttribute("toastType", "success");
                return "fragments/admin-toast :: toast";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", message);
                return "redirect:/admin";
            }
        } catch (Exception ex) {
            String errorMsg = "Reindex failed: " + ex.getMessage();

            if (request.getHeader("HX-Request") != null) {
                model.addAttribute("toastMessage", errorMsg);
                model.addAttribute("toastType", "error");
                return "fragments/admin-toast :: toast";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", errorMsg);
                return "redirect:/admin";
            }
        }
    }
}
