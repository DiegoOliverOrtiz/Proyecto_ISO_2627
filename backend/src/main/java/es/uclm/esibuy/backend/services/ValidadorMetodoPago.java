package es.uclm.esibuy.backend.services;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import es.uclm.esibuy.backend.dto.MetodoPagoRequest;
import es.uclm.esibuy.backend.model.TipoMetodoPago;

@Component
public class ValidadorMetodoPago {

	private static final String CAMPO_TIPO = "tipo";
	private static final String CAMPO_NUMERO_TARJETA = "numeroTarjeta";
	private static final String CAMPO_TITULAR = "titular";
	private static final String CAMPO_CADUCIDAD = "caducidad";
	private static final String CAMPO_CVV = "cvv";
	private static final String CAMPO_CORREO_PAYPAL = "correoPaypal";
	private static final String CAMPO_TELEFONO_BIZUM = "telefonoBizum";

	private static final Pattern NUMERO_TARJETA = Pattern.compile("\\d{13,19}");
	private static final Pattern TITULAR = Pattern.compile("[\\p{L} .'-]{2,60}");
	private static final Pattern CADUCIDAD = Pattern.compile("(0[1-9]|1[0-2])/(\\d{2}|\\d{4})");
	private static final Pattern CVV = Pattern.compile("\\d{3,4}");
	private static final Pattern TELEFONO_BIZUM = Pattern.compile("[67]\\d{8}");
	private static final int LONGITUD_MAXIMA_CORREO = 254;

	public record Resultado(
			Map<String, List<String>> errores,
			String datoPrincipal,
			String titular,
			String caducidad) {

		public boolean esValido() {
			return errores.isEmpty();
		}
	}

	public Resultado validar(String codigoTipo, MetodoPagoRequest request) {
		Map<String, List<String>> errores = new LinkedHashMap<>();
		return switch (codigoTipo) {
			case TipoMetodoPago.CODIGO_TARJETA -> validarTarjeta(request, errores);
			case TipoMetodoPago.CODIGO_PAYPAL -> validarPaypal(request, errores);
			case TipoMetodoPago.CODIGO_BIZUM -> validarBizum(request, errores);
			default -> {
				agregarError(errores, CAMPO_TIPO, "Este tipo de método de pago no está soportado.");
				yield new Resultado(errores, null, null, null);
			}
		};
	}

	private Resultado validarTarjeta(MetodoPagoRequest request, Map<String, List<String>> errores) {
		String numero = validarNumeroTarjeta(request.numeroTarjeta(), errores);
		String titular = validarTitular(request.titular(), errores);
		String caducidad = validarCaducidad(request.caducidad(), errores);
		validarCvv(request.cvv(), errores);
		return new Resultado(errores, numero, titular, caducidad);
	}

	private String validarNumeroTarjeta(String valor, Map<String, List<String>> errores) {
		String numero = limpiar(valor).replace(" ", "").replace("-", "");
		if (numero.isEmpty()) {
			agregarError(errores, CAMPO_NUMERO_TARJETA, "El número de tarjeta es obligatorio.");
		} else if (!NUMERO_TARJETA.matcher(numero).matches() || !cumpleLuhn(numero)) {
			agregarError(errores, CAMPO_NUMERO_TARJETA, "El número de tarjeta no es válido.");
		}
		return numero;
	}

	private String validarTitular(String valor, Map<String, List<String>> errores) {
		String titular = limpiar(valor);
		if (titular.isEmpty()) {
			agregarError(errores, CAMPO_TITULAR, "El titular es obligatorio.");
		} else if (!TITULAR.matcher(titular).matches()) {
			agregarError(errores, CAMPO_TITULAR, "El titular contiene caracteres no permitidos o su longitud no es válida.");
		}
		return titular;
	}

	private String validarCaducidad(String valor, Map<String, List<String>> errores) {
		String texto = limpiar(valor);
		if (texto.isEmpty()) {
			agregarError(errores, CAMPO_CADUCIDAD, "La caducidad es obligatoria.");
			return null;
		}
		Matcher matcher = CADUCIDAD.matcher(texto);
		if (!matcher.matches()) {
			agregarError(errores, CAMPO_CADUCIDAD, "La caducidad debe tener el formato MM/AA.");
			return null;
		}
		int mes = Integer.parseInt(matcher.group(1));
		int anio = Integer.parseInt(matcher.group(2));
		if (anio < 100) {
			anio += 2000;
		}
		if (YearMonth.of(anio, mes).isBefore(YearMonth.now())) {
			agregarError(errores, CAMPO_CADUCIDAD, "La tarjeta está caducada.");
			return null;
		}
		return String.format(Locale.ROOT, "%02d/%02d", mes, anio % 100);
	}

	private void validarCvv(String valor, Map<String, List<String>> errores) {
		String cvv = limpiar(valor);
		if (cvv.isEmpty()) {
			agregarError(errores, CAMPO_CVV, "El código de seguridad es obligatorio.");
		} else if (!CVV.matcher(cvv).matches()) {
			agregarError(errores, CAMPO_CVV, "El código de seguridad debe tener 3 o 4 dígitos.");
		}
	}

	private Resultado validarPaypal(MetodoPagoRequest request, Map<String, List<String>> errores) {
		String correo = limpiar(request.correoPaypal()).toLowerCase(Locale.ROOT);
		if (correo.isEmpty()) {
			agregarError(errores, CAMPO_CORREO_PAYPAL, "El correo de PayPal es obligatorio.");
		} else if (!correoValido(correo)) {
			agregarError(errores, CAMPO_CORREO_PAYPAL, "El correo de PayPal no es válido.");
		}
		return new Resultado(errores, correo, null, null);
	}

	private Resultado validarBizum(MetodoPagoRequest request, Map<String, List<String>> errores) {
		String telefono = limpiar(request.telefonoBizum()).replace(" ", "");
		if (telefono.startsWith("+34")) {
			telefono = telefono.substring(3);
		}
		if (telefono.isEmpty()) {
			agregarError(errores, CAMPO_TELEFONO_BIZUM, "El teléfono de Bizum es obligatorio.");
		} else if (!TELEFONO_BIZUM.matcher(telefono).matches()) {
			agregarError(errores, CAMPO_TELEFONO_BIZUM, "El teléfono de Bizum no es válido.");
		}
		return new Resultado(errores, telefono, null, null);
	}

	private boolean correoValido(String correo) {
		int arroba = correo.indexOf('@');
		if (arroba <= 0 || arroba != correo.lastIndexOf('@') || correo.length() > LONGITUD_MAXIMA_CORREO) {
			return false;
		}
		String dominio = correo.substring(arroba + 1);
		int punto = dominio.lastIndexOf('.');
		return punto > 0
				&& punto < dominio.length() - 1
				&& correo.chars().noneMatch(Character::isWhitespace);
	}

	private boolean cumpleLuhn(String digitos) {
		int suma = 0;
		boolean duplicar = false;
		for (int i = digitos.length() - 1; i >= 0; i--) {
			int digito = digitos.charAt(i) - '0';
			if (duplicar) {
				digito *= 2;
				if (digito > 9) {
					digito -= 9;
				}
			}
			suma += digito;
			duplicar = !duplicar;
		}
		return suma % 10 == 0;
	}

	private String limpiar(String valor) {
		return valor == null ? "" : valor.trim();
	}

	private void agregarError(Map<String, List<String>> errores, String campo, String mensaje) {
		errores.computeIfAbsent(campo, clave -> new ArrayList<>()).add(mensaje);
	}
}