package com.hookledger.web;

import com.hookledger.service.LedgerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EventListController {

    private final LedgerService ledgerService;

    public EventListController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/")
    public String listEvents(Model model) {
        model.addAttribute("events", ledgerService.listEvents());
        return "events";
    }
}
