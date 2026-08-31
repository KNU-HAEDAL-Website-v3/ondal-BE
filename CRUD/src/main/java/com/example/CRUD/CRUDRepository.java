package com.example.CRUD;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CRUDRepository extends JpaRepository<CRUD, Integer> {
    
}