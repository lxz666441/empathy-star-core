package com.lxz.empathystar.core;

/** In-memory disclosure state shared by inline links and term entries. */
public final class KnowledgeDisclosure {
    private String active = "";

    public boolean toggle(String term) {
        if (term == null || term.isEmpty() || term.equalsIgnoreCase(active)) {
            close();
            return false;
        }
        active = term;
        return true;
    }

    public void close() { active = ""; }
}
