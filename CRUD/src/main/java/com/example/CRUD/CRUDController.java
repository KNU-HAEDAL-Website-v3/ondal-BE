package com.example.CRUD;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class CRUDController {
    @Autowired
    private CRUDService crudService;

    @GetMapping("/write")
    public String writeForm() {
        return "write";
    }

    @PostMapping("/writePro")
    public String writePro(CRUD crud) {
        crudService.write(crud);
        return "redirect:/list";
    }

    @GetMapping("/list")
    public String list(Model model) {
        model.addAttribute("list", crudService.list());
        return "list";
    }

    @GetMapping("/view")
    public String view(Model model, Integer id){
        model.addAttribute("crud", crudService.view(id));
        return "view";
    }

    @GetMapping("/delete")
    public String delete(Integer id){
        crudService.delete(id);
        return "redirect:/list";
    }
    @GetMapping("/modify")
    public String update(Model model, Integer id){
        model.addAttribute("crud", crudService.view(id));
        return "modify";
    }

    @PostMapping("/modifyPro")
    public String modifyPro(CRUD crud){
        crudService.write(crud);
        return "redirect:/list";
    }
}