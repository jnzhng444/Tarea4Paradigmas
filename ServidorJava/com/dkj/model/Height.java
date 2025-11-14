package com.dkj.model;


import java.util.Objects;

public final class Height {
    private final String value; // no exponemos int en el dominio
    public Height(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Height");
        this.value = value;
    }
    public String value(){ return value; }
    @Override public String toString(){ return value; }
    @Override public boolean equals(Object o){ return (o instanceof Height h) && value.equals(h.value); }
    @Override public int hashCode(){ return Objects.hash(value); }
}
