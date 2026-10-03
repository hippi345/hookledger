package com.hookledger.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InvoiceDeskController {

    @GetMapping({"/invoice-desk", "/invoice-desk/"})
    public String invoiceDesk() {
        return "forward:/invoice-desk/index.html";
    }
}
