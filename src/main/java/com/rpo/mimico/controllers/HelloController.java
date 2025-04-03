package com.rpo.mimico.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/auth/login")
    public String hello() {
        return "This is a public endpoint.";
    }
}
