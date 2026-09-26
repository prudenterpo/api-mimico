package com.rpo.mimico.services;

import com.rpo.mimico.entities.WordEntity;

import java.util.List;

public interface WordSelector {

    WordEntity pick(List<WordEntity> candidates);
}
