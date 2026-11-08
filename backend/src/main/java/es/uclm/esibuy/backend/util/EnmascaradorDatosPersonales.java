package es.uclm.esibuy.backend.util;

public final class EnmascaradorDatosPersonales {

    private EnmascaradorDatosPersonales() {
    }

    public static String dniNif(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor;
        }

        int caracteresVisibles = Math.min(2, valor.length());
        return valor.substring(0, caracteresVisibles)
                + "*".repeat(Math.max(0, valor.length() - caracteresVisibles));
    }

    public static String telefono(String valor) {
        if (valor == null || valor.isBlank()) {
            return valor;
        }

        String digitos = valor.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return "*".repeat(valor.length());
        }

        int digitosVisibles = Math.min(3, Math.max(0, digitos.length() - 1));
        return "*".repeat(digitos.length() - digitosVisibles)
                + digitos.substring(digitos.length() - digitosVisibles);
    }
}
