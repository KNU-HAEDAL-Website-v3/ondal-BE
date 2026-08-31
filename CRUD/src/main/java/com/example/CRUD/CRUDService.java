package com.example.CRUD;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CRUDService {
    @Autowired
    private CRUDRepository crudRepository;

    public void write(CRUD crud){
        crudRepository.save(crud);
    }

    public List<CRUD> list() {
        return crudRepository.findAll();
    }

    public CRUD view(Integer id) {
        return crudRepository.findById(id).get();
    }

    public void delete(Integer id) {
        crudRepository.deleteById(id);
    }
}