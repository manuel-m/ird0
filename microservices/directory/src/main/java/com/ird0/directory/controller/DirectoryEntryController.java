package com.ird0.directory.controller;

import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.service.DirectoryEntryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${directory.api.base-path:/api/entries}")
public class DirectoryEntryController {

    private final DirectoryEntryService service;

    public DirectoryEntryController(DirectoryEntryService service) {
        this.service = service;
    }

    @GetMapping
    public List<DirectoryEntry> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public DirectoryEntry getOne(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public DirectoryEntry create(@RequestBody DirectoryEntry entry) {
        return service.create(entry);
    }

    @PutMapping("/{id}")
    public DirectoryEntry update(@PathVariable Long id, @RequestBody DirectoryEntry entry) {
        return service.update(id, entry);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
