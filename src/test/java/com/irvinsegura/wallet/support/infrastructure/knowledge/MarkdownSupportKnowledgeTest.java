package com.irvinsegura.wallet.support.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.shared.domain.Language;
import com.irvinsegura.wallet.support.domain.model.BotReply;
import com.irvinsegura.wallet.support.domain.model.KnowledgeDocument;
import com.irvinsegura.wallet.support.domain.model.KnowledgeIndex;
import com.irvinsegura.wallet.support.domain.model.KnowledgeSection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Asks the real files what people would really ask. Worth adding a line here when someone asks something it should
 * have answered.
 */
class MarkdownSupportKnowledgeTest {

    private final MarkdownSupportKnowledge knowledge = new MarkdownSupportKnowledge();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ¿Cómo deposito dinero?                        | ¿Cómo deposito dinero?
            quiero recargar mi billetera con tarjeta      | ¿Cómo deposito dinero?
            como le hago para depositar                   | ¿Cómo deposito dinero?
            como transfiero a otra persona                | ¿Cómo transfiero dinero a otra persona?
            quiero mandar dinero a un amigo               | ¿Cómo transfiero dinero a otra persona?
            como retiro a mi banco                        | ¿Cómo retiro dinero a mi banco?
            quiero sacar dinero                           | ¿Cómo retiro dinero a mi banco?
            cuanto puedo retirar al dia                   | ¿Cuál es mi límite diario?
            cual es el limite diario                      | ¿Cuál es mi límite diario?
            donde descargo mi estado de cuenta            | ¿Cómo descargo mi estado de cuenta?
            necesito un comprobante                       | ¿Cómo obtengo un comprobante?
            olvidé mi contraseña                          | Olvidé mi contraseña
            como activo el 2fa                            | ¿Cómo activo la verificación en dos pasos?
            como agrego una cuenta bancaria               | ¿Cómo registro una cuenta bancaria?
            como cierro mis sesiones                      | ¿Cómo veo o cierro mis sesiones?
            como cambio el idioma                         | ¿Cómo cambio mi idioma?
            quiero cobrar con un qr                       | ¿Cómo cobro con un enlace o un QR?
            hola                                          | Hola
            muchas gracias                                | Gracias
            cual es el limite de deposito al mes          | ¿Hay un límite para depositar?
            cual es el limite mensual                     | ¿Cuál es mi límite diario?
            cual es mi limte diario                       | ¿Cuál es mi límite diario?
            como retrio a mi bacno                        | ¿Cómo retiro dinero a mi banco?
            quiero trasferir a otra persona               | ¿Cómo transfiero dinero a otra persona?
            olvide mi contrasena                          | Olvidé mi contraseña
            como deposito                                 | ¿Cómo deposito dinero?
            cuanto puedo depositar                        | ¿Hay un límite para depositar?
            cuanto cobran                                 | ¿Cuánto cobran? ¿Hay comisiones?
            hay comision por retirar                      | ¿Cuánto cobran? ¿Hay comisiones?
            cuanto tarda un retiro                        | ¿Cuánto tarda?
            puedo depositar en efectivo                   | ¿Puedo depositar en efectivo o desde mi banco?
            como recibo dinero                            | ¿Cómo recibo dinero?
            por que me pide mi contraseña al enviar       | ¿Por qué me pide mi contraseña o un código al enviar?
            puedo cambiar pesos a dolares                 | ¿Puedo cambiar pesos a dólares?
            quiero facturar                               | ¿Me dan factura?
            que es el folio                               | ¿Qué es el folio?
            perdi mi telefono                             | Perdí mi teléfono o mi app de autenticación
            como cambio mi contraseña                     | ¿Cómo cambio mi contraseña?
            cuales son los requisitos de la contraseña    | ¿Qué requisitos tiene la contraseña?
            como cambio mi nombre                         | ¿Cómo cambio mi nombre o mi teléfono?
            puedo enviar una foto                         | ¿Puedo enviar archivos por el chat?
            la app no carga                               | La app no carga o me marca un error
            como activo el modo oscuro                    | ¿Cómo activo el modo oscuro?
            como instalo la app                           | ¿Hay una app para el celular?
            por que se cerro mi sesion                    | ¿Por qué se cerró mi sesión?
            como elimino una cuenta bancaria              | ¿Cómo elimino una cuenta bancaria?
            cuantas cuentas bancarias puedo tener         | ¿Cuántas cuentas bancarias puedo tener?
            mi clabe no es valida                         | Mi CLABE no es válida
            como abro una billetera                       | ¿Qué billeteras tengo?
            donde veo mi saldo                            | ¿Dónde veo mi saldo?
            no puedo retirar                              | No me deja retirar o enviar dinero
            como veo el estado de mi retiro               | ¿Cómo veo el estado de mi retiro?
            mi retiro fue rechazado                       | Mi retiro fue rechazado
            puedo retirar dolares                         | ¿Puedo retirar dólares?
            me rechazaron mi tarjeta                      | Mi pago con tarjeta no pasó
            me mandaron un enlace de cobro                | Me mandaron un enlace de cobro
            como cierro sesion                            | ¿Cómo cierro sesión?
            como desactivo el 2fa                         | ¿Cómo desactivo la verificación en dos pasos?
            que divisas manejan                           | ¿Qué divisas manejan?
            que divisas acepta el sistema                 | ¿Qué divisas manejan?
            cuales son las divisas                        | ¿Qué divisas manejan?
            en que monedas puedo tener mi dinero          | ¿Qué divisas manejan?
            tienen euros                                  | ¿Qué divisas manejan?
            que es una divisa                             | ¿Qué es una divisa?
            cual es el tipo de cambio                     | ¿Cuál es el tipo de cambio?
            a como esta el dolar                          | ¿Cuál es el tipo de cambio?
            puedo cambiar divisas                         | ¿Cuál es el tipo de cambio?
            puedo enviar dolares a alguien con pesos      | ¿Puedo enviar dólares a alguien que solo tiene pesos?
            puedo depositar dolares                       | ¿Puedo depositar dólares?
            puedo tener dos billeteras en pesos           | ¿Puedo tener varias billeteras en la misma moneda?
            puedo pasar dinero entre mis billeteras       | ¿Puedo pasar dinero entre mis billeteras?
            mi saldo genera intereses                     | ¿Mi saldo genera intereses?
            quiero invertir en criptomonedas              | ¿Ofrecen inversiones, préstamos o criptomonedas?
            pueden darme un préstamo hipotecario          | ¿Ofrecen inversiones, préstamos o criptomonedas?
            tienen tarjeta fisica                         | ¿Tienen tarjeta propia o pago de servicios?
            para que sirve esta app                       | ¿Qué puedo hacer con la billetera?
            cual es la diferencia entre enviar y retirar  | ¿Cuál es la diferencia entre enviar y retirar?
            que es spei                                   | ¿Qué es SPEI?
            que es una clabe                              | ¿Qué es la CLABE?
            que es el concepto                            | ¿Qué es el concepto?
            puedo enviar a alguien sin cuenta             | ¿Puedo enviar dinero a alguien sin cuenta?
            puedo programar pagos automaticos             | ¿Puedo programar pagos automáticos?
            hay un monto minimo                           | ¿Hay un monto mínimo?
            cuando se reinicia mi limite                  | ¿Cuándo se reinicia mi límite diario?
            que tarjetas aceptan                          | ¿Qué tarjetas aceptan?
            guardan los datos de mi tarjeta               | ¿Guardan los datos de mi tarjeta?
            no tengo saldo suficiente                     | Me sale que no tengo saldo suficiente
            mi enlace ya expiro                           | Mi enlace no funciona o ya expiró
            que es la verificacion en dos pasos           | ¿Qué es la verificación en dos pasos?
            mi codigo no funciona                         | Mi código de verificación no funciona
            puedo usar varios dispositivos                | ¿Puedo usar mi cuenta en varios dispositivos?
            que puede ver soporte                         | ¿Qué puede ver soporte de mi cuenta?
            cuanto tardan en atenderme                    | ¿Cuánto tardan en atenderme?
            de que me avisan                              | ¿De qué me avisan?
            me pueden decir de que forma meto plata a mi cuenta | ¿Cómo deposito dinero?
            ya no quiero tener mi cuenta aqui como la borro | ¿Cómo cierro mi cuenta?
            se puede pagar con tarjeta de debito          | ¿Qué tarjetas aceptan?
            cuanto tiempo tarda en llegar el dinero a mi banco | ¿Cuánto tarda?
            hay algun cobro por mandar dinero             | ¿Cuánto cobran? ¿Hay comisiones?
            puedo tener dolares en la app                 | ¿Qué divisas manejan?
            como se si ya me llego una transferencia      | ¿Cómo recibo dinero?
            me equivoque de persona al enviar puedo cancelarlo | ¿Puedo cancelar una transferencia?
            donde veo lo que he gastado                   | ¿Cómo busco un movimiento?
            es seguro guardar mi dinero aqui              | ¿Cómo protejo mi cuenta?
            que pasa si pierdo mi celular                 | Perdí mi teléfono o mi app de autenticación
            se me olvido la clave                         | Olvidé mi contraseña
            como cambio mi correo                         | ¿Puedo cambiar mi correo?
            puedo cambiar mi nombre                       | ¿Cómo cambio mi nombre o mi teléfono?
            para que sirve la clabe                       | ¿Qué es la CLABE?
            como le paso dinero a mi mama                 | ¿Cómo transfiero dinero a otra persona?
            que es el spei                                | ¿Qué es SPEI?
            como cobro a alguien que me debe              | ¿Cómo cobro con un enlace o un QR?
            cuantas cuentas de banco puedo registrar      | ¿Cuántas cuentas bancarias puedo tener?
            por que no puedo usar mi cuenta recien agregada | Acabo de agregar una cuenta y no la puedo usar
            cuanto es lo maximo que puedo depositar       | ¿Hay un límite para depositar?
            tienen app para android                       | ¿Hay una app para el celular?
            cuanto cuesta usar esto                       | ¿Cuánto cobran? ¿Hay comisiones?
            puedo convertir pesos a dolares               | ¿Puedo cambiar pesos a dólares?
            me dan intereses por mi saldo                 | ¿Mi saldo genera intereses?
            no me llego el correo de verificacion         | ¿Cómo verifico mi correo?
            como descargo un recibo de mi envio           | ¿Cómo obtengo un comprobante?
            quien puede ver mis movimientos               | ¿Qué puede ver soporte de mi cuenta?
            a que hora atienden                           | ¿Cuánto tardan en atenderme?
            como apago las notificaciones                 | ¿Cómo elijo qué notificaciones recibo?
            como depocito dinero                          | ¿Cómo deposito dinero?
            kiero retirar mi dinero                       | ¿Cómo retiro dinero a mi banco?
            olvide mi contrasena                          | Olvidé mi contraseña
            cual es el limte diario                       | ¿Cuál es mi límite diario?
            como trasnfiero a otra persona                | ¿Cómo transfiero dinero a otra persona?
            komo cambio mi idioma                         | ¿Cómo cambio mi idioma?
            hice un retiro hace tres dias y no ha llegado | Mi retiro no ha llegado
            me cobraron dos veces el deposito             | Mi depósito no aparece
            alguien entro a mi cuenta sin permiso         | No reconozco un movimiento
            envie dinero y la otra persona dice que no le llego | Envié dinero y la otra persona dice que no le llegó
            me bloquearon la cuenta y no se por que       | Mi billetera está congelada
            desaparecio dinero de mi billetera            | Mi saldo está mal
            cuanto saldo tengo                            | ¿Dónde veo mi saldo?
            cual fue mi ultimo movimiento                 | ¿Cómo busco un movimiento?
            me recomiendas invertir en bitcoin            | ¿Ofrecen inversiones, préstamos o criptomonedas?
            y eso cuanto tarda                            | ¿Cuánto tarda?
            como deposito y cuanto tarda en reflejarse    | ¿Cuánto tarda?
            como le agrego saldo a mi billetera           | ¿Cómo deposito dinero?
            Cual es el limite de depsoito al mes??        | ¿Hay un límite para depositar?
            mi deposito sigue en proceso                  | Mi depósito no aparece
            me cobraron de mas                            | Mi depósito no aparece
            no puedo entrar a mi cuenta                   | Mi cuenta se bloqueó por intentos fallidos
            como pago un qr                               | Me mandaron un enlace de cobro
            tienen tarjeta                                | ¿Tienen tarjeta propia o pago de servicios?
            donde veo mi clabe                            | ¿Qué es la CLABE?
            como cambio mi numero de celular              | ¿Cómo cambio mi nombre o mi teléfono?
            puedo exportar a excel                        | ¿Cómo descargo mi estado de cuenta?
            como veo mis ingresos del mes                 | ¿Qué muestra la gráfica de mi saldo?
            como busco una transferencia por nombre       | ¿Cómo busco un movimiento?
            me llego un codigo que no pedi                | Me pidieron mi contraseña o llegó un mensaje sospechoso
            como elimino mi cuenta                        | ¿Cómo cierro mi cuenta?
            quiero quitar la verificacion en dos pasos    | ¿Cómo desactivo la verificación en dos pasos?
            como me doy de alta                           | ¿Cómo creo una cuenta?
            donde esta mi dinero                          | ¿Dónde veo mi saldo?
            el retiro lleva dos dias                      | Mi retiro no ha llegado
            llegue al limite                              | Alcancé mi límite diario
            la aplicacion esta muy lenta                  | La app no carga o me marca un error
            me aparece error                              | La app no carga o me marca un error
            quien eres                                    | ¿Cómo funciona este chat?
            eres un robot                                 | ¿Cómo funciona este chat?
            nadie me contesta                             | ¿Cuánto tardan en atenderme?
            quiero cargar dinero a mi cuenta              | ¿Cómo deposito dinero?
            puedo mandar un pdf por el chat               | ¿Puedo enviar archivos por el chat?
            mi contraseña no la acepta                    | ¿Qué requisitos tiene la contraseña?
            me pueden regresar una transferencia que hice mal | ¿Puedo cancelar una transferencia?
            cuando me llega mi retiro                     | ¿Cuánto tarda?
            quiero ver mi historial                       | ¿Cómo descargo mi estado de cuenta?
            donde pongo mi clabe                          | ¿Cómo registro una cuenta bancaria?
            como activo la doble autenticacion            | ¿Cómo activo la verificación en dos pasos?
            como le hago para que me paguen               | ¿Cómo recibo dinero?
            me mandaron un link para pagar                | Me mandaron un enlace de cobro
            cual es el dolar hoy                          | ¿Cuál es el tipo de cambio?
            como pongo modo noche                         | ¿Cómo activo el modo oscuro?
            mi saldo no coincide                          | Mi saldo está mal
            me quitaron dinero                            | Mi saldo está mal
            me llego un correo raro                       | Me pidieron mi contraseña o llegó un mensaje sospechoso
            mi retiro sigue pendiente                     | Mi retiro no ha llegado
            que pasa si mi retiro falla                   | Mi retiro fue rechazado
            como saco mi dinero al banco                  | ¿Cómo retiro dinero a mi banco?
            quiero pasar mi saldo a mi cuenta de banco    | ¿Cómo retiro dinero a mi banco?
            como transfiero a mi cuenta bancaria          | ¿Cómo retiro dinero a mi banco?
            puedo retirar todo mi saldo                   | ¿Cómo retiro dinero a mi banco?
            cuanto tarda en llegar mi retiro              | ¿Cuánto tarda?
            se puede retirar en dolares                   | ¿Puedo retirar dólares?
            a donde llega un retiro en dolares            | ¿Puedo retirar dólares?
            como se si mi retiro ya llego                 | ¿Cómo veo el estado de mi retiro?
            que significa retiro en camino                | ¿Cómo veo el estado de mi retiro?
            por que rechazaron mi retiro                  | Mi retiro fue rechazado
            que significa retiro devuelto                 | ¿Qué significa cada tipo de movimiento?
            puedo retirar a la cuenta de otra persona     | ¿Puedo pagarle a la cuenta bancaria de otra persona?
            la cuenta bancaria tiene que estar a mi nombre | ¿Cómo registro una cuenta bancaria?
            puedo retirar en fin de semana                | ¿Cuánto tarda?
            por que no puedo retirar a mi cuenta nueva    | Acabo de agregar una cuenta y no la puedo usar
            cobran por retirar                            | ¿Cuánto cobran? ¿Hay comisiones?
            hay minimo para retirar                       | ¿Hay un monto mínimo?
            no puedo retirar y no se por que              | No me deja retirar o enviar dinero
            el boton de retirar esta deshabilitado        | No me deja retirar o enviar dinero
            como meto dinero a mi cuenta                  | ¿Cómo deposito dinero?
            puedo pagar con mi tarjeta de credito         | ¿Qué tarjetas aceptan?
            puedo depositar desde oxxo                    | ¿Puedo depositar en efectivo o desde mi banco?
            puedo depositar por transferencia desde mi banco | ¿Puedo depositar en efectivo o desde mi banco?
            cuanto dura la pagina para pagar el deposito  | ¿Cuánto tiempo tengo para pagar un depósito?
            cerre la pagina de pago sin pagar             | ¿Cuánto tiempo tengo para pagar un depósito?
            que significa deposito expirado               | ¿Cuánto tiempo tengo para pagar un depósito?
            cancele el pago con tarjeta                   | Mi pago con tarjeta no pasó
            me rechazaron la tarjeta al depositar         | Mi pago con tarjeta no pasó
            que significa pago reembolsado                | ¿Qué significa cada tipo de movimiento?
            como le mando lana a mi hermano               | ¿Cómo transfiero dinero a otra persona?
            el dinero llega al momento cuando transfiero  | ¿Cuánto tarda?
            puedo transferir a una cuenta de banco de otra persona | ¿Puedo pagarle a la cuenta bancaria de otra persona?
            puedo enviar dinero a mi otra billetera       | ¿Puedo pasar dinero entre mis billeteras?
            no encuentro a la persona para enviarle       | No encuentra a la persona a la que quiero enviar
            como le hago para cobrarle a alguien          | ¿Cómo cobro con un enlace o un QR?
            como genero un qr para que me paguen          | ¿Cómo cobro con un enlace o un QR?
            como comparto mi enlace de cobro              | ¿Cómo cobro con un enlace o un QR?
            me mandaron un qr como lo pago                | Me mandaron un enlace de cobro
            el enlace de cobro no funciona                | El enlace de cobro no funciona
            para que sirve el concepto                    | ¿Qué es el concepto?
            como se a quien le estoy enviando             | ¿Cómo sé que le envío a la persona correcta?
            que pasa si le doy dos veces al boton de enviar | ¿Qué pasa si doy clic dos veces?
            puedo programar una transferencia             | ¿Puedo programar pagos automáticos?
            cuanto es lo maximo que puedo mandar en un dia | ¿Cuál es mi límite diario?
            a que hora se reinicia el limite              | ¿Cuándo se reinicia mi límite diario?
            ya no me deja enviar por el limite            | Alcancé mi límite diario
            cobran comision por depositar                 | ¿Cuánto cobran? ¿Hay comisiones?
            tiene algun costo mensual                     | ¿Cuánto cobran? ¿Hay comisiones?
            cuantas billeteras puedo tener                | ¿Puedo tener varias billeteras en la misma moneda?
            como abro una billetera en dolares            | ¿Qué billeteras tengo?
            es seguro guardar dinero aqui                 | ¿Cómo protejo mi cuenta?
            hay un maximo de saldo                        | ¿Hay un tope de saldo?
            como oculto mi saldo                          | ¿Dónde veo mi saldo?
            como descargo mis movimientos en excel        | ¿Cómo descargo mi estado de cuenta?
            como imprimo mi estado de cuenta              | ¿Cómo descargo mi estado de cuenta?
            estado de cuenta de meses anteriores          | ¿Cómo descargo mi estado de cuenta?
            como comparto el comprobante                  | ¿Cómo obtengo un comprobante?
            que es el folio de un movimiento              | ¿Qué es el folio?
            donde encuentro el folio                      | ¿Qué es el folio?
            como filtro mis movimientos por fecha         | ¿Cómo busco un movimiento?
            los movimientos se pueden borrar              | ¿Se pueden borrar mis movimientos?
            necesito factura                              | ¿Me dan factura?
            que pasa si elimino una cuenta bancaria con un retiro en camino | ¿Cómo elimino una cuenta bancaria?
            mi clabe dice que es invalida                 | Mi CLABE no es válida
            puedo editar mi cuenta bancaria               | ¿Puedo editar una cuenta bancaria?
            como me registro                              | ¿Cómo creo una cuenta?
            se me olvido mi contraseña que hago           | Olvidé mi contraseña
            cuanto dura el link para cambiar la contraseña | Mi enlace no funciona o ya expiró
            no me llega ningun correo                     | No me llegan los correos
            por que me cerro la sesion solo               | ¿Por qué se cerró mi sesión?
            como veo en que dispositivos tengo sesion     | ¿Cómo veo o cierro mis sesiones?
            como cambio mi numero de telefono             | ¿Cómo cambio mi nombre o mi teléfono?
            como apago los correos de notificaciones      | ¿Cómo elijo qué notificaciones recibo?
            puedo usar la app en mi celular               | ¿Hay una app para el celular?
            puedo tener cuenta de empresa                 | ¿Hay cuentas para empresas?
            puedo tener dos cuentas                       | ¿Puedo tener más de una cuenta?
            no puedo iniciar sesion                       | Mi cuenta se bloqueó por intentos fallidos
            me dice correo o contraseña incorrectos       | Me dice que mi correo o contraseña son incorrectos
            como activo la autenticacion de dos factores  | ¿Cómo activo la verificación en dos pasos?
            que hago si pierdo mi celular y tengo 2fa     | Perdí mi teléfono o mi app de autenticación
            cambie de celular como paso mi autenticador   | Perdí mi teléfono o mi app de autenticación
            cuantos codigos de recuperacion me quedan     | ¿Qué son los códigos de recuperación?
            el codigo de 6 digitos no funciona            | Mi código de verificación no funciona
            el chat es un robot                           | ¿Cómo funciona este chat?
            cuanto tardan en atenderme en soporte         | ¿Cuánto tardan en atenderme?
            puedo mandar una captura de pantalla          | ¿Puedo enviar archivos por el chat?
            ver mis chats anteriores                      | ¿Puedo ver mis conversaciones anteriores?
            ok gracias                                    | Gracias
            como hago un retiro                           | ¿Cómo retiro dinero a mi banco?
            quiero mandar mi dinero a mi cuenta de bbva   | ¿Cómo retiro dinero a mi banco?
            cuanto se tarda el spei                       | ¿Cuánto tarda?
            los retiros funcionan en domingo              | ¿Cuánto tarda?
            que quiere decir en proceso en mi retiro      | ¿Cómo veo el estado de mi retiro?
            cuantas clabes puedo agregar                  | ¿Cuántas cuentas bancarias puedo tener?
            me equivoque al escribir mi clabe al registrarla | ¿Puedo editar una cuenta bancaria?
            como agrego mi cuenta de banco                | ¿Cómo registro una cuenta bancaria?
            por que tengo que esperar 24 horas            | Acabo de agregar una cuenta y no la puedo usar
            como recargo mi billetera                     | ¿Cómo deposito dinero?
            aceptan american express                      | ¿Qué tarjetas aceptan?
            me salio tarjeta declinada                    | Mi pago con tarjeta no pasó
            como le pago a alguien                        | ¿Cómo transfiero dinero a otra persona?
            como le pido dinero a alguien                 | ¿Cómo cobro con un enlace o un QR?
            cuanto puedo enviar                           | ¿Cuál es mi límite diario?
            cuando puedo volver a enviar                  | ¿Cuándo se reinicia mi límite diario?
            hay comisiones                                | ¿Cuánto cobran? ¿Hay comisiones?
            como bajo mi estado de cuenta en pdf          | ¿Cómo descargo mi estado de cuenta?
            quiero el comprobante de mi deposito          | ¿Cómo obtengo un comprobante?
            como encuentro una transferencia vieja        | ¿Cómo busco un movimiento?
            no recuerdo mi contraseña                     | Olvidé mi contraseña
            como cierro sesion en otro celular            | ¿Cómo veo o cierro mis sesiones?
            como pongo la verificacion en dos pasos       | ¿Cómo activo la verificación en dos pasos?
            cambie de cel y no tengo el autenticador      | Perdí mi teléfono o mi app de autenticación
            me bloqueo por intentos                       | Mi cuenta se bloqueó por intentos fallidos
            como pongo el tema oscuro                     | ¿Cómo activo el modo oscuro?
            la pagina se queda en blanco                  | La app no carga o me marca un error
            tienen app en la play store                   | ¿Hay una app para el celular?
            hola buenas tardes                            | Hola
            muchas gracias por la ayuda                   | Gracias
            como retiro a banorte                         | ¿Cómo retiro dinero a mi banco?
            retiro minimo                                 | ¿Hay un monto mínimo?
            el retiro tiene costo                         | ¿Cuánto cobran? ¿Hay comisiones?
            puedo agregar una cuenta de nu                | ¿Cómo registro una cuenta bancaria?
            cuanto tiempo guardan mis notificaciones      | ¿Cómo marco mis notificaciones como leídas?
            que hago si no reconozco una sesion           | ¿Cómo veo o cierro mis sesiones?
            donde veo mis codigos de recuperacion         | ¿Qué son los códigos de recuperación?
            """)
    void answersInSpanish(String question, String title) {
        assertThat(knowledge.answer(question, Language.ES)).map(KnowledgeSection::title).contains(title);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            how do I deposit money                        | How do I deposit money?
            I want to send money to a friend              | How do I transfer money to someone?
            how do I withdraw to my bank                  | How do I withdraw money to my bank?
            what is the daily limit                       | What is my daily limit?
            where can I download my statement             | How do I download my statement?
            I forgot my password                          | I forgot my password
            hello                                         | Hello
            what is the monthly deposit limit             | Is there a limit on deposits?
            how do I withdarw to my bank                  | How do I withdraw money to my bank?
            are there any fees                            | Are there any fees?
            how long does a withdrawal take               | How long does it take?
            can I deposit cash                            | Can I deposit cash or from my bank?
            how do I receive money                        | How do I receive money?
            can I change pesos to dollars                 | Can I change pesos to dollars?
            do you issue invoices                         | Do you issue invoices?
            what is the reference                         | What is the reference?
            I lost my phone                               | I lost my phone or my authenticator app
            how do I change my password                   | How do I change my password?
            how do I change my name                       | How do I change my name or phone?
            can I send a photo                            | Can I send files in the chat?
            the app is not loading                        | The app won't load or shows an error
            how do I turn on dark mode                    | How do I turn on dark mode?
            why was I signed out                          | Why was I signed out?
            how do I remove a bank account                | How do I remove a bank account?
            can I withdraw dollars                        | Can I withdraw dollars?
            my card was declined                          | My card payment didn't go through
            how do I turn on 2fa                          | How do I turn on two-factor authentication?
            how do I turn off 2fa                         | How do I turn off two-factor authentication?
            what currencies do you handle                 | What currencies do you handle?
            which currencies does the system accept       | What currencies do you handle?
            do you have euros                             | What currencies do you handle?
            what is the exchange rate                     | What is the exchange rate?
            can I deposit dollars                         | Can I deposit dollars?
            can I send dollars to someone who has pesos   | Can I send dollars to someone who only has pesos?
            does my balance earn interest                 | Does my balance earn interest?
            do you offer loans                            | Do you offer investments, loans or crypto?
            what is spei                                  | What is SPEI?
            what is a clabe                               | What is a CLABE?
            is there a minimum amount                     | Is there a minimum amount?
            what can support see                          | What can support see of my account?
            when does my daily limit reset                | When does my daily limit reset?
            what cards do you accept                      | What cards do you accept?
            what is two-factor authentication             | What is two-factor authentication?
            how can I put money in my account             | How do I deposit money?
            how long until the money reaches my bank      | How long does it take?
            i forgot my pasword                           | I forgot my password
            can I hold dollars here                       | What currencies do you handle?
            I withdrew three days ago and it never arrived | My withdrawal hasn't arrived
            someone got into my account                   | I don't recognize a movement
            what is my balance                            | Where do I see my balance?
            how do I delete my account                    | How do I close my account?
            can I cancel a transfer I sent by mistake     | Can I cancel a transfer?
            my transfer is stuck                          | I sent money and the other person says it didn't arrive
            how do I reset my password                    | I forgot my password
            can I have more than one wallet               | Can I have several wallets in the same currency?
            how do I move my balance to my bank           | How do I withdraw money to my bank?
            can I withdraw everything                     | How do I withdraw money to my bank?
            how do I cash out                             | How do I withdraw money to my bank?
            can I withdraw on the weekend                 | How long does it take?
            how do I know if my withdrawal arrived        | How do I check my withdrawal's status?
            what does in progress mean on my withdrawal   | How do I check my withdrawal's status?
            why was my withdrawal rejected                | My withdrawal was rejected
            where does a dollar withdrawal go             | Can I withdraw dollars?
            which banks do you accept                     | Which banks do you accept?
            can I withdraw to bbva                        | How do I withdraw money to my bank?
            the withdraw button is disabled               | It won't let me withdraw or send money
            why do I have to wait 24 hours                | I just added an account and can't use it
            how many clabes can I add                     | How many bank accounts can I have?
            can I edit my bank account                    | Can I edit a bank account?
            I closed the payment page without paying      | How long do I have to pay a deposit?
            how do I generate a qr to get paid            | How do I get paid with a link or a QR code?
            how do I pay with a qr                        | Someone sent me a payment link
            the payment link doesn't work                 | The payment link doesn't work
            does the payment link expire                  | Does a payment link expire or can it be paid twice?
            what is the concept for                       | What is the concept?
            who am I sending to                           | How do I know I'm sending to the right person?
            does the transfer arrive instantly            | How long does it take?
            when can I send again                         | When does my daily limit reset?
            is it safe to keep money here                 | How do I protect my account?
            how do I find an old transfer                 | How do I find a movement?
            where do I see the reference                  | What is the reference?
            it says my email or password is wrong         | It says my email or password is wrong
            how long does the reset link last             | My link doesn't work or expired
            I don't get any emails                        | My emails don't arrive
            it keeps signing me out                       | Why was I signed out?
            can I have two accounts                       | Can I have more than one account?
            my 6-digit code doesn't work                  | My verification code doesn't work
            can I send a screenshot                       | Can I send files in the chat?
            the page stays blank                          | The app won't load or shows an error
            is it on the play store                       | Is there a mobile app?
            what do I do if I don't recognize a session   | How do I see or end my sessions?
            where are my recovery codes                   | What are recovery codes?
            thanks                                        | Thanks
            """)
    void answersInEnglish(String question, String title) {
        assertThat(knowledge.answer(question, Language.EN)).map(KnowledgeSection::title).contains(title);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no reconozco un cargo", "me robaron, fue un fraude", "mi retiro no ha llegado",
            "mi billetera está congelada", "quiero hablar con una persona",
            "puedo cancelar una transferencia", "puedo cancelar un retiro",
            "pueden aumentar mi limite", "mi deposito no aparece", "quiero un reembolso", "me falta dinero",
            "no tengo mis codigos de recuperacion", "mi cuenta esta deshabilitada"})
    void whatItCanOnlyExplainGoesToAPersonToo(String question) {
        Optional<KnowledgeSection> answer = knowledge.answer(question, Language.ES);

        assertThat(answer).map(KnowledgeSection::escalate).contains(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"I don't recognize a charge", "talk to a person",
            "can I cancel a transfer", "my wallet is frozen", "I want a refund"})
    void whatItCanOnlyExplainGoesToAPersonInEnglishToo(String question) {
        Optional<KnowledgeSection> answer = knowledge.answer(question, Language.EN);

        assertThat(answer).map(KnowledgeSection::escalate).contains(true);
    }

    // A problem with money or the account goes to a person, whether its answer hands it over or its words say so
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            mi retiro no ha llegado y ya pasaron dos dias
            saque dinero y no esta en mi banco
            el retiro salio de mi billetera pero no llego a mi cuenta
            mi retiro lleva horas en proceso
            mi retiro sigue en camino desde ayer
            retire a una clabe equivocada
            me equivoque de cuenta al retirar
            puse mal la clabe y ya hice el retiro
            el banco dice que no recibio nada
            mi retiro aparece como pagado pero no lo veo
            el retiro dice enviado pero mi banco no lo tiene
            mi retiro fue rechazado y no me regresaron el dinero
            retiro devuelto pero no veo el dinero
            no me llega el dinero que retire
            no me regresaron mi retiro
            me descontaron el retiro dos veces
            el retiro se hizo dos veces
            quiero cancelar un retiro
            tengo un problema con un retiro
            mi banco me rechazo el dinero y no aparece en mi billetera
            hice un retiro que no fui yo
            mi deposito sigue en proceso desde ayer
            me descontaron de mas
            me cobraron de mas en la tarjeta
            mi banco me cobro y la app dice que fallo
            le transferi a la persona equivocada
            me equivoque de correo al enviar
            envie mas dinero del que queria
            mande dinero y mi primo dice que no le llego
            quiero que me suban el limite
            el saldo que veo no es el correcto
            mi saldo esta en cero y tenia dinero
            me falta dinero en mi billetera
            mi billetera esta congelada
            por que congelaron mi billetera
            puedo cerrar mi billetera de dolares
            hay un cargo que no reconozco
            no reconozco una transferencia
            aparece un movimiento que no hice
            veo dos movimientos iguales
            necesito una aclaracion de un movimiento
            me llego un correo de que agregaron una cuenta bancaria y no fui yo
            alguien agrego una clabe que no es mia
            me suspendieron la cuenta
            alguien entro a mi cuenta
            me robaron el celular con la app abierta
            alguien cambio mi contraseña
            perdi mis codigos de recuperacion y mi celular
            quiero hablar con un humano
            pasame con un asesor
            oigan mi retiro de ayer nunca llego
            hace una semana retire y nada
            retire 500 pesos y mi banco no muestra nada
            ya me descontaron el retiro pero no me llega
            mi retiro dice fallido y no tengo mi dinero
            me sale retiro pagado pero en el banco no esta
            mande el retiro a una cuenta que no era
            me cobraron un retiro doble
            me hackearon
            mi saldo bajo sin razon
            deposite 1000 y solo me aparecen 500
            pague el deposito y sigue en proceso
            se cobro en mi tarjeta pero el deposito fallo
            le envie a otra persona por error
            me equivoque al escribir el correo
            mi transferencia no le llego a mi mama
            me congelaron la billetera
            necesito que me ayuden con una aclaracion
            mi dinero no llego a santander
            lleva 3 horas mi retiro
            el retiro no se ha reflejado
            cancelen mi retiro por favor
            me robaron mi cuenta
            no me han regresado mi dinero del retiro rechazado
            mi deposito no se refleja
            me cobraron y no me dieron saldo
            le mande a quien no era
            """)
    void problemsGoToAPerson(String message) {
        assertThat(knowledge.reply(message, Language.ES).escalate()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            my withdrawal never arrived
            I withdrew money and it's not in my bank
            my bank says they didn't receive anything
            it says paid but my bank doesn't show it
            my withdrawal has been in progress for hours
            I withdrew to the wrong clabe
            I typed the clabe wrong and already withdrew
            my withdrawal was rejected and I didn't get my money back
            the withdrawal was returned but I don't see the money
            my withdrawal happened twice
            I want to cancel a withdrawal
            someone added a bank account I don't recognize
            I paid by card and the money isn't there
            my card was charged but the deposit failed
            I deposited 1000 and only got 500
            my deposit is not reflected
            I sent money to the wrong person
            I sent more than I wanted
            I typed the email wrong when sending
            my friend says the transfer never arrived
            my balance is wrong
            my balance went down for no reason
            there is a transaction I didn't make
            someone withdrew money from my account
            I see two identical movements
            my phone was stolen
            someone changed my password
            I lost my recovery codes and my phone
            """)
    void problemsGoToAPersonInEnglishToo(String message) {
        assertThat(knowledge.reply(message, Language.EN).escalate()).isTrue();
    }

    // Changing the email and closing the account are self-service, so the bot explains them without handing over
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            quiero cambiar mi correo                      | ¿Puedo cambiar mi correo?
            como cambio el correo de mi cuenta            | ¿Puedo cambiar mi correo?
            quiero cerrar mi cuenta                       | ¿Cómo cierro mi cuenta?
            quiero borrar mi cuenta                       | ¿Cómo cierro mi cuenta?
            como verifico mi identidad                    | ¿Cómo verifico mi identidad?
            por que me piden mi ine para retirar          | ¿Por qué me piden mi INE para retirar?
            rechazaron mi ine                             | Rechazaron mi verificación de identidad
            cuanto tarda la revision de mi ine            | ¿Cuándo revisan mi INE?
            me aparece un abono de ajuste                 | ¿Qué es un abono o cargo de ajuste?
            """)
    void selfServiceAnswersStayWithTheBot(String message, String title) {
        assertThat(knowledge.answer(message, Language.ES)).map(KnowledgeSection::title).contains(title);
        assertThat(knowledge.reply(message, Language.ES).escalate()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            can I change my email                         | Can I change my email?
            how do I change the email of my account       | Can I change my email?
            how do I close my account                     | How do I close my account?
            how do I verify my identity                   | How do I verify my identity?
            why do you ask for my ine to withdraw         | Why do you ask for my INE to withdraw?
            they rejected my ine                          | My identity verification was rejected
            how long does the review of my ine take       | When is my INE reviewed?
            what is an adjustment debit                   | What is an adjustment credit or debit?
            """)
    void selfServiceAnswersStayWithTheBotInEnglishToo(String message, String title) {
        assertThat(knowledge.answer(message, Language.EN)).map(KnowledgeSection::title).contains(title);
        assertThat(knowledge.reply(message, Language.EN).escalate()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"mi perro se comió el teléfono de mi hermana", "me gusta el futbol los domingos", "asdf qwer",
            "¿?"})
    void whatItDoesNotKnowGetsNoAnswer(String question) {
        assertThat(knowledge.answer(question, Language.ES)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"k", "?", "...", "😀", "asdf", "asdf qwer", "kkkkk", "jajaja", "xd", "qwerty", "fhsjk dlkgj",
            "ayuda", "como", "no", "perro"})
    void whatItCannotReadIsAskedAgainWithoutBotheringAPerson(String message) {
        BotReply reply = knowledge.reply(message, Language.ES);

        assertThat(reply.escalate()).isFalse();
        assertThat(reply.asksFeedback()).isFalse();
        assertThat(reply.suggestions()).isEmpty();
        assertThat(reply.text()).contains("No te entendí");
    }

    @Test
    void itAsksAgainInEnglishToo() {
        assertThat(knowledge.reply("k", Language.EN).text()).contains("I didn't understand");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            como retiro a mi ksjdh                        | ¿Cómo retiro dinero a mi banco?
            asdf como deposito qwer                       | ¿Cómo deposito dinero?
            cual es mi limite diario jajaja               | ¿Cuál es mi límite diario?
            """)
    void aWordItCannotReadAmongOthersItCanIsIgnored(String question, String title) {
        assertThat(knowledge.answer(question, Language.ES)).map(KnowledgeSection::title).contains(title);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            como retiro a mi bnaoc                        | ¿Cómo retiro dinero a mi banco?
            depositar vakita                              | ¿Cómo deposito dinero?
            y en dolares                                  | ¿Qué billeteras tengo?
            ayuda con la tarjeta del banco                | Mi pago con tarjeta no pasó
            """)
    void whenItUnderstandsOnlyAPartItOffersWhatMayHaveBeenMeant(String message, String first) {
        BotReply reply = knowledge.reply(message, Language.ES);

        assertThat(reply.suggestions()).hasSizeBetween(1, 3).first().isEqualTo(first);
        assertThat(reply.escalate()).isFalse();
        assertThat(reply.text()).contains("entendí solo una parte");
    }

    @Test
    void twoQuestionsAtOnceOfferBothRatherThanAnsweringOne() {
        assertThat(knowledge.reply("como deposito y como retiro", Language.ES).suggestions())
                .contains("¿Cómo deposito dinero?");
    }

    @Test
    void wordsThatMeanTheSameAreOneWord() {
        assertThat(knowledge.answer("quiero meter lana", Language.ES)).map(KnowledgeSection::title)
                .contains("¿Cómo deposito dinero?");
    }

    @Test
    void aWordThatIsAlreadyRightIsNotAnotherOneMistyped() {
        assertThat(knowledge.answer("saldo", Language.ES)).map(KnowledgeSection::title)
                .contains("¿Dónde veo mi saldo?");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mi perro se comió el teléfono de mi hermana", "me gusta el futbol los domingos",
            "cual es la capital de francia"})
    void aSentenceItCanReadButKnowsNothingAboutIsAskedAnotherWay(String message) {
        BotReply reply = knowledge.reply(message, Language.ES);

        assertThat(reply.escalate()).isFalse();
        assertThat(reply.unanswered()).isTrue();
        assertThat(reply.text()).contains("Todavía no tengo información");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tengo un problema con mi retiro", "tengo un problema con mi perro", "quiero poner una queja",
            "necesito una aclaracion", "es urgente", "tengo un problema con mi retiro de mi banco"})
    void someoneReportingAProblemGoesToAPerson(String message) {
        assertThat(knowledge.reply(message, Language.ES).escalate()).isTrue();
    }

    @Test
    void aProblemKeepsAnAnswerThatAlreadyHandsItToAPerson() {
        BotReply reply = knowledge.reply("tengo un problema, mi retiro no ha llegado", Language.ES);

        assertThat(reply.escalate()).isTrue();
        assertThat(reply.text()).contains("Como este no ha llegado");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ok", "vale", "ok gracias", "listo", "adios"})
    void sayingOkOrGoodbyeIsNotAQuestion(String message) {
        assertThat(knowledge.answer(message, Language.ES)).map(KnowledgeSection::smallTalk).contains(true);
    }

    // What the bot offers comes back as a message: the title has to lead to its own section, whatever else matches
    @ParameterizedTest
    @EnumSource(Language.class)
    void everyTitleLeadsToItsOwnSection(Language language) throws IOException {
        for (KnowledgeSection section : sections(language)) {
            if (section.reply() == null) {
                assertThat(knowledge.answer(section.title(), language)).map(KnowledgeSection::title)
                        .contains(section.title());
            }
        }
    }

    @Test
    void anIndexWithoutWhatToSayWhenItFindsNoAnswerIsAMistake() {
        List<KnowledgeSection> sections = KnowledgeDocument.parse("""
                ## Hello
                keywords: hello

                Hi!""");

        assertThatThrownBy(() -> new KnowledgeIndex(sections)).isInstanceOf(IllegalStateException.class);
    }

    private static List<KnowledgeSection> sections(Language language) throws IOException {
        List<KnowledgeSection> sections = new ArrayList<>();
        for (Resource file : new PathMatchingResourcePatternResolver()
                .getResources("classpath:support/knowledge/" + language.tag() + "/*.md")) {
            try (InputStream in = file.getInputStream()) {
                sections.addAll(KnowledgeDocument.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        return sections;
    }

    @Test
    void greetingsAreNotAnswersToRate() {
        assertThat(knowledge.answer("hola", Language.ES)).map(KnowledgeSection::smallTalk).contains(true);
        assertThat(knowledge.answer("como deposito", Language.ES)).map(KnowledgeSection::smallTalk).contains(false);
    }
}
