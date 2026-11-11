# Billetera Digital

API REST de billetera digital con un libro mayor de partida doble, construida como un monolito modular con arquitectura
limpia.

**Stack:** Java 25, Spring Boot 4.1, Spring Security (servidor de recursos OAuth2, JWT RS256), PostgreSQL 17, Flyway,
Spring Modulith, ArchUnit, Testcontainers, Docker, Micrometer (Prometheus, trazas con OpenTelemetry), Bucket4j,
Grafana, Tempo, Spring Mail y Mailpit, Stripe Checkout.

## Aplicación web

La app para clientes, agentes de soporte y administradores (React + TypeScript) vive en su propio repositorio,
[BilleteraDigitalWeb](https://github.com/IrvinSegura/BilleteraDigitalWeb).

| | |
| --- | --- |
| ![Inicio del cliente: saldo, entradas y salidas, gráfica del saldo y actividad reciente](https://raw.githubusercontent.com/IrvinSegura/BilleteraDigitalWeb/main/docs/screenshots/customer-dashboard.png) | ![Estados de cuenta, eligiendo las fechas en un calendario](https://raw.githubusercontent.com/IrvinSegura/BilleteraDigitalWeb/main/docs/screenshots/customer-statements.png) |
| Inicio del cliente | Estados de cuenta |
| ![Un caso de soporte: las operaciones del cliente, lo escalado y el chat](https://raw.githubusercontent.com/IrvinSegura/BilleteraDigitalWeb/main/docs/screenshots/support-case.png) | ![Inicio de administración](https://raw.githubusercontent.com/IrvinSegura/BilleteraDigitalWeb/main/docs/screenshots/admin-home.png) |
| Un caso de soporte | Administración |

**[Ver las 37 capturas, por rol, en el repositorio del front →](https://github.com/IrvinSegura/BilleteraDigitalWeb#capturas)**

## Arquitectura

```
com.irvinsegura.wallet
├── shared          núcleo abierto: Money, DomainException, manejo de errores, correlation id, límites de peticiones
├── identity        usuarios, autenticación, tokens
├── accounts        billeteras, estado, límites
├── ledger          diario de partida doble, saldos, conciliación
├── transfers       depósitos, retiros (pagos SPEI por STP), transferencias (idempotentes)
├── payments        proveedor de pagos (Stripe o simulado), checkout, webhooks, reembolsos
├── audit           bitácora de auditoría de solo inserción (listener de eventos)
├── notifications   bandeja en la app y correos (listener de eventos)
└── support         chat con un bot que responde desde archivos Markdown, y luego con un agente
```

Cada módulo sigue la misma estructura, y las dependencias siempre apuntan hacia adentro:

```
web ──> application ──> domain <── infrastructure
```

- `domain` no depende de ningún framework.
- Los límites entre módulos los verifica `ModularityTests` (Spring Modulith) y las reglas de capas
  `CleanArchitectureTests` (ArchUnit). Romper cualquiera de los dos hace fallar el build.

## Correr en local

Requisitos: JDK 25 y Docker.

```bash
cp .env.example .env   # luego llena MAIL_USERNAME, MAIL_PASSWORD y EMAIL_FROM
docker compose up -d
./mvnw spring-boot:run
```

- PostgreSQL escucha en el puerto 5440 del host (`DB_PORT`) para no chocar con otras bases de datos locales.
- Los correos son reales, se envían por Gmail como en producción, así que usa direcciones que puedas leer. El perfil
  dev lee `.env` por sí mismo, así que al correr desde el IDE no hacen falta variables de entorno extra.
- API: http://localhost:8080. Actuator tiene su propio puerto, como en producción:
  http://localhost:8081/actuator/health y http://localhost:8081/actuator/prometheus
- Documentación de la API (solo perfil dev): http://localhost:8080/swagger-ui.html

### Usuarios de la base de datos

| Usuario | Lo usa | Privilegios |
|---|---|---|
| `wallet_owner` | Flyway | Dueño del esquema, corre las migraciones |
| `wallet_app` | La aplicación | Solo acceso a datos; las tablas del libro mayor, de conciliación y de auditoría son de solo inserción (sin UPDATE/DELETE) |

El usuario de la app lo crea `docker/postgres/init` la primera vez que se inicializa el volumen.
Para reiniciar la base de datos: `docker compose down -v`.

### Correr todo en contenedores

```bash
docker compose --profile app up -d --build
```

### Problemas en Windows

Si el arranque falla con `Unable to establish loopback connection`, el JDK no puede crear su socket interno en
`%TEMP%` (pasa cuando el nombre del usuario de Windows tiene espacios y `%TEMP%` es una ruta corta con `~1`).
Agrega esta opción de VM a la configuración de ejecución, apuntando a cualquier carpeta existente con una ruta corta:

```
-Djdk.net.unixdomain.tmpdir=C:\tmp
```

## Autenticación

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/api/v1/auth/register` | pública | Registro (`email`, `password`, `fullName`, `captchaToken`); siempre `202`, tenga o no cuenta el correo |
| POST | `/api/v1/auth/login` | pública | Obtiene un token de acceso (15 min) y uno de renovación (7 días); con doble factor activo, envía también `twoFactorCode`. `403 EMAIL_NOT_VERIFIED` hasta que se verifique el correo; envía `"resendVerification": true` para recibir un enlace nuevo |
| POST | `/api/v1/auth/refresh` | pública | Cambia un token de renovación por un par nuevo; el anterior deja de funcionar |
| POST | `/api/v1/auth/logout` | pública | Revoca el token de renovación y todos los que se rotaron a partir de él |
| POST | `/api/v1/auth/email-verification/confirm` | pública | Confirma el correo con el token del enlace enviado (`204`) |
| POST | `/api/v1/auth/password-reset` | pública | Envía por correo un enlace para restablecer (`{"email", "captchaToken"}`); siempre `202`, esté registrado o no |
| POST | `/api/v1/auth/password-reset/confirm` | pública | Pone una contraseña nueva con el token del enlace; cierra todas las sesiones y quita un bloqueo |
| GET | `/api/v1/users/me` | bearer | Perfil del usuario autenticado, con `phone`, `status`, `emailVerified`, `twoFactorEnabled` y `mustChangePassword` |
| PATCH | `/api/v1/users/me` | bearer | Cambia `fullName` (de 1 a 100 caracteres, espacios repetidos unidos) y `phone` (E.164 como `+525512345678`; se quitan espacios, guiones y paréntesis, `""` lo borra); un campo omitido conserva su valor. Si no, `422 INVALID_PROFILE` |
| PUT | `/api/v1/users/me/language` | bearer | Define el idioma de los correos del usuario (`{"language": "es"}` o `"en"`); la app web lo mantiene igual al que muestra |
| GET | `/api/v1/users/me/sessions` | bearer | Dispositivos con sesión: user agent, dirección IP, cuándo empezó y cuándo se usó por última vez, y cuál es la `current` |
| DELETE | `/api/v1/users/me/sessions/{id}` | bearer | Cierra una sesión (`204`; `404 SESSION_NOT_FOUND` si no es tuya o ya terminó) |
| DELETE | `/api/v1/users/me/sessions` | bearer | Cierra todas las sesiones menos la actual (`{"revoked": 2}`) |
| POST | `/api/v1/users/me/email-verification` | bearer | Envía un enlace de verificación nuevo (`202`); los anteriores dejan de funcionar |
| POST | `/api/v1/users/me/password` | bearer | Cambia la contraseña (`currentPassword`, `newPassword`); cierra todas las demás sesiones y devuelve tokens nuevos. `422 PASSWORD_UNCHANGED` si la nueva es la actual |
| POST | `/api/v1/users/me/two-factor` | bearer | Empieza a configurar una app de autenticación (`{"currentPassword"}`): devuelve `secret` y `otpauthUri` para un código QR (`201`) |
| POST | `/api/v1/users/me/two-factor/confirm` | bearer | Lo activa con un código de la app (`{"code"}`); devuelve 10 códigos de recuperación, que se muestran una sola vez |
| POST | `/api/v1/users/me/two-factor/disable` | bearer | Lo desactiva (`{"currentPassword", "code"}`, un código de la app o uno de recuperación) (`204`) |
| GET | `/api/v1/users/me/two-factor` | bearer | Si está activo y cuántos códigos de recuperación quedan |

Todos los demás endpoints requieren `Authorization: Bearer <accessToken>`. Cinco inicios de sesión fallidos seguidos
bloquean la cuenta 15 minutos.

Las contraseñas (al registrarse, cambiarla y restablecerla) necesitan de 12 a 64 caracteres y ninguna mezcla en
particular (si no, `422 WEAK_PASSWORD`), siguiendo NIST SP 800-63B. Las fáciles de adivinar se rechazan con
`422 COMMON_PASSWORD`: contraseñas y palabras comunes (`common-passwords.txt` junto a `PasswordPolicy`), esas mismas
disfrazadas con dígitos, símbolos, acentos o caracteres parecidos (`P@ssw0rd2024!`, `Contraseña123`), repeticiones,
secuencias y recorridos del teclado (`123456789012`, `qwertyuiopas`) y el propio nombre o correo del usuario
(`AnaLopez1990`). Las que pasan esas reglas se buscan después en Pwned Passwords de Have I Been Pwned y se rechazan con
`422 BREACHED_PASSWORD` cuando aparecen en una filtración conocida. Solo se envían los primeros 5 caracteres del SHA-1
de la contraseña (k-anonimato) y las respuestas vienen con relleno. La búsqueda se rinde a los 2 segundos y deja pasar
la contraseña, así que una caída nunca bloquea los registros; corre antes de la transacción de la base de datos. Se
apaga con `PASSWORD_BREACH_CHECK=false` (las pruebas lo hacen).

Cerrar una sesión revoca su token de renovación al momento, así que ya no se puede renovar; un token de acceso que ya
tenga sigue siendo válido hasta que expira (15 minutos como máximo). Los tokens de acceso llevan el id de la sesión en el
claim `sid`, que es como se distingue la `current`.

### Apps web

Una app de navegador debe enviar `X-Session-Mode: cookie` al iniciar sesión, renovar, cerrar sesión y cambiar la
contraseña. Así, el token de renovación viaja solo en una cookie `HttpOnly`, `Secure`, `SameSite=Strict` limitada a
`/api/v1/auth`, de modo que los scripts de la página (y un fallo XSS) nunca lo ven: la respuesta JSON deja
`refreshToken` en null, y `POST /api/v1/auth/refresh` con ese encabezado y sin cuerpo usa la cookie. Sin el
encabezado, la cookie se ignora, y una página de otro sitio no puede agregarlo, así que las peticiones falsificadas
desde otro sitio no pueden usarla. Guarda el token de acceso solo en memoria. Los clientes móviles y de servidor siguen
enviando y recibiendo el token de renovación en el cuerpo, como antes.

La API responde CORS solo para los orígenes de `CORS_ALLOWED_ORIGINS` (separados por comas, orígenes exactos, con
credenciales); ninguno por defecto. Un servidor de desarrollo que redirige `/api` a `localhost:8080` no necesita
ninguno. `SameSite=Strict` significa que la app y la API deben compartir sitio, por ejemplo `app.example.com` y
`api.example.com`.

### Autenticación de doble factor

Los usuarios pueden agregar una app de autenticación (TOTP: 6 dígitos, pasos de 30 segundos, cualquier app como Google
Authenticator o 1Password). Hasta que se confirma el primer código no cambia nada, así que una configuración abandonada
a medias nunca deja a nadie fuera. Activarlo cierra todas las sesiones, también la de quien lo activa, para que una
sesión robada antes no pueda seguir; el siguiente inicio de sesión pide el código. Una vez activo:

- El inicio de sesión responde `403 TWO_FACTOR_REQUIRED` tras una contraseña correcta sin `twoFactorCode`; un código
  incorrecto es un inicio fallido (`401`) y cuenta para el bloqueo, que es lo que hace inútil adivinar seis dígitos.
- Cada código funciona una vez, así que un código visto por encima del hombro ya no sirve después. Un código de
  recuperación (`xxxxx-xxxxx`) funciona donde funciona uno de la app, también una sola vez.
- Agregar una cuenta bancaria también necesita `twoFactorCode`, y desactivar el doble factor necesita la contraseña y
  un código.
- Soporte puede desactivarlo a quien perdió su teléfono y sus códigos de recuperación
  (`POST /api/v1/admin/users/{id}/two-factor/reset`, con un motivo); se avisa al usuario y queda en la auditoría.
- Los administradores deben tenerlo: hasta que lo activan y vuelven a iniciar sesión con un código, todos los
  endpoints `/api/v1/admin/**` responden `403 ADMIN_TWO_FACTOR_REQUIRED` (`wallet.identity.admin-requires-two-factor`,
  apagado solo en las pruebas). Los tokens de acceso llevan un claim `two_factor` para esto.

Los secretos de las apps se guardan cifrados con AES-256-GCM. La llave viene de `TWO_FACTOR_ENCRYPTION_KEY` (Base64 de
32 bytes aleatorios, `openssl rand -base64 32`); producción se niega a arrancar sin ella, y cambiarla deshabilita
todas las apps ya configuradas. Los perfiles dev y test usan llaves fijas.

### Verificación de correo y recuperación de contraseña

Al registrarse se envía un enlace para confirmar la dirección, y la cuenta no puede iniciar sesión hasta seguirlo: con
la contraseña correcta, el inicio de sesión responde `403 EMAIL_NOT_VERIFIED`, y con `"resendVerification": true`
también envía un enlace nuevo. El registro responde `202` sin cuerpo tenga o no cuenta la dirección (la contraseña se
cifra en ambos casos, así que el tiempo de respuesta también coincide), así que no sirve para averiguar quién está
registrado. Solo el dueño se entera: una cuenta sin verificar recibe un enlace nuevo, y una verificada un correo que
dice que alguien intentó registrarse con ella. Los retiros y las transferencias también revisan la verificación
(`403 EMAIL_NOT_VERIFIED`), para las sesiones abiertas antes de esta regla.

Las solicitudes de registro y de restablecimiento de contraseña envían correo a cualquier dirección que reciban, así
que también necesitan un token de Cloudflare Turnstile (`captchaToken`; si no, `422 CAPTCHA_FAILED`). Está activo en
producción (`CAPTCHA_ENABLED`, con `TURNSTILE_SECRET_KEY`; la app web necesita la llave de sitio correspondiente) y
apagado en los perfiles dev y test. A diferencia de la búsqueda de contraseñas filtradas, falla cerrado: si no se puede
contactar a Cloudflare en 3 segundos, la solicitud se rechaza. Las llaves de prueba de Cloudflare (llave de sitio
`1x00000000000000000000AA`, secreto `1x0000000000000000000000000000000AA`) siempre pasan, para probarlo en local.

Los enlaces apuntan a la app cliente (`wallet.notifications.email.app-url`, `APP_URL` en producción) con el token en el
fragmento (`/verify-email#token=...`, `/reset-password#token=...`); la app envía el token a los endpoints de arriba.
Cada enlace funciona una vez y solo funciona el más reciente: los de verificación duran 24 horas y los de
restablecimiento 30 minutos. Solo se guarda un hash de cada token, y los tokens nunca se escriben en el outbox de
eventos. En local, los correos también llegan a la bandeja real, por Gmail.

### Llaves de firma

Los perfiles dev y test generan un par de llaves RSA temporal al arrancar, así que reiniciar la app invalida todos los
tokens. Producción requiere un par fijo:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

Monta ambos archivos como secretos y apunta `JWT_PRIVATE_KEY_LOCATION` y `JWT_PUBLIC_KEY_LOCATION` a ellos
(por ejemplo, `file:/run/secrets/jwt-private.pem`). Git ignora los archivos `*.pem`.

## Billeteras y depósitos

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/wallets` | Abre una billetera (`{"currency": "MXN"}`); una por moneda, se admiten MXN y USD |
| GET | `/api/v1/wallets` | Tus billeteras con sus saldos |
| GET | `/api/v1/wallets/{id}` | Una billetera; las de otros usuarios responden `404` |
| GET | `/api/v1/wallets/{id}/movements?page=0&size=20` | Movimientos, los más recientes primero, con el saldo después de cada uno y, en las transferencias, `counterpartyName` y `description`. Filtros, todos opcionales: `from` y `to` (días del calendario, ambos incluidos), `type` (repetible: `DEPOSIT`, `TRANSFER`, `WITHDRAWAL`, `WITHDRAWAL_REVERSAL`...), `direction` (`IN`, `OUT`) y `q` (hasta 60 caracteres: el inicio del folio, o parte del nombre de la otra persona o de la descripción, sin importar mayúsculas ni acentos; lee las 1,000 transferencias más recientes de la billetera que coincidan) |
| GET | `/api/v1/wallets/{id}/movements/{transactionId}` | Un movimiento, para su comprobante (`404 MOVEMENT_NOT_FOUND`) |
| GET | `/api/v1/wallets/{id}/statement?from=2026-11-01&to=2026-11-30` | Los movimientos de un periodo (366 días como máximo, el mes actual por defecto) como descarga CSV, los más antiguos primero, con la otra persona y la descripción de cada transferencia |
| GET | `/api/v1/wallets/{id}/summary?month=2026-11` | `openingBalance`, entradas y salidas y `closingBalance` de un mes del calendario (el actual por defecto), sumados sobre todos los movimientos |
| GET | `/api/v1/wallets/{id}/limits` | El límite diario de salidas, lo usado hoy, lo que queda y cuándo se reinicia |
| GET | `/api/v1/wallets/{id}/balance-history?days=30` | Saldo de cierre de cada uno de los últimos `days` días (de 1 a 366, hoy incluido), más `openingBalance` del día anterior; se omiten los días antes de que se abriera la billetera. Los días y meses siguen `wallet.accounts.limit-zone` |
| POST | `/api/v1/wallets/{id}/deposits` | Recarga por el proveedor de pagos (`{"amount": 100.50}`, como máximo 100,000.00 MXN o 5,000.00 USD por depósito; si no, `422 DEPOSIT_TOO_LARGE`); necesita `Idempotency-Key` |
| GET | `/api/v1/wallets/{id}/deposits/{depositId}` | Un depósito y su estado, para seguirlo después del checkout |
| POST | `/api/v1/wallets/{id}/withdrawals` | Pago al banco del usuario (`{"amount": 100, "bankAccountId": "..."}`, cuenta bancaria obligatoria para MXN); necesita `Idempotency-Key` |
| POST | `/api/v1/bank-accounts` | Agrega una CLABE (`{"clabe": "...", "holderName": "...", "twoFactorCode": "..."}`, el código solo con doble factor activo); 5 como máximo, solo se devuelven los últimos cuatro dígitos |
| GET | `/api/v1/bank-accounts` | Tus cuentas bancarias |
| DELETE | `/api/v1/bank-accounts/{id}` | Borra una (`204`); los retiros que ya van en camino sí llegan |
| GET | `/api/v1/wallets/{id}/withdrawals/{withdrawalId}` | Un retiro y el estado de su pago |

Todos requieren un token bearer. Los montos se devuelven como cadenas decimales (`{"amount": "100.50", "currency":
"MXN"}`) para que los clientes nunca pierdan centavos por el punto flotante. El dinero se registra en un libro mayor de
partida doble.

### Depósitos y el proveedor de pagos

Un depósito se acredita solo cuando el proveedor de pagos confirma el dinero; el proveedor se elige con
`PAYMENT_PROVIDER`:

- `simulated` (por defecto fuera de producción): el pago se completa al momento, así que el depósito responde `201`
  con `"status": "COMPLETED"` y el saldo nuevo.
- `stripe`: el depósito responde `202` con `"status": "PENDING"` y un `checkoutUrl` de Stripe Checkout (solo
  tarjetas), válido por 30 minutos. Stripe regresa al pagador a `wallet.payments.return-url` con
  `?payment=<id del depósito>`, y el cliente consulta el depósito hasta que pasa a `COMPLETED`, `EXPIRED` o `REJECTED`.

Stripe confirma los pagos con un webhook firmado a `POST /api/v1/payments/webhooks/stripe`; cada evento se aplica una
vez, sin importar cuántas veces llegue, y un proceso le pregunta a Stripe por los checkouts cuyo webhook nunca llegó.
Si la billetera se congeló o la cuenta se deshabilitó mientras el pagador pagaba, el depósito queda `REJECTED`, no se
acredita nada y el pago se reembolsa automáticamente. Si no se puede contactar a Stripe, el depósito falla con
`503 PAYMENT_PROVIDER_UNAVAILABLE`.

Para probar Stripe en local con llaves de prueba y el [Stripe CLI](https://docs.stripe.com/stripe-cli):

```bash
stripe listen --forward-to localhost:8080/api/v1/payments/webhooks/stripe
```

Luego arranca la app con `PAYMENT_PROVIDER=stripe`, `STRIPE_API_KEY=sk_test_...` y `STRIPE_WEBHOOK_SECRET` con el valor
`whsec_...` que imprimió el comando, abre el `checkoutUrl` de un depósito y paga con la tarjeta `4242 4242 4242 4242`.

### Retiros

El dinero sale de la billetera en cuanto se pide un retiro y espera en una cuenta de pagos en tránsito hasta que el
banco confirma el pago. La respuesta es `201` con `"status": "PAID"` cuando el pago ya está confirmado (siempre, con el
proveedor simulado) o `202` con `"status": "PENDING"` mientras va en camino. Si el banco rechaza el pago, el retiro
pasa a `FAILED` con un `failureReason`, el dinero regresa a la billetera (movimiento de tipo `WITHDRAWAL_REVERSAL`) y
se avisa al usuario. Un proceso reenvía cada minuto los pagos que el proveedor nunca recibió y revisa los que van en
camino. La alerta `PayoutStuck` se dispara cuando uno lleva más de 30 minutos esperando.

#### Pagos SPEI con STP

Con `PAYOUT_PROVIDER=stp`, los retiros en MXN se pagan por SPEI a través de STP. Cada uno se registra como una orden
(SpeiWS `ordenPago/registra`), firmada con la llave RSA de la plataforma (SHA-256 sobre la "cadena original" de STP), y
el proceso de pagos lee su estado por clave de rastreo cada minuto (EFWS `consultaOrden`): `LQ` paga el retiro; `D`
(devuelto por el banco receptor), `CN` o un rechazo (`RB` de Banxico, `RL`, `RA`, `CL`, `CA`, `EL`, `RE`) lo hace fallar
con `failureReason` `STP_RETURNED_<estado>_<causa>`; y una orden que STP rechaza falla al momento con
`STP_REJECTED_<código>`. En cualquier caso, el dinero regresa a la billetera. Una respuesta que no es un rechazo del
pago (id `0`: sin respuesta, una firma inválida o un campo faltante; `-1`: una clave de rastreo que STP ya tiene donde
la consulta no llega) lo deja pendiente, así que se reenvía y se dispara la alerta de atorado, pero nunca se paga dos
veces.

La clave de rastreo (`BDW` + el día en que se pidió el retiro + 20 caracteres de su id, 29 en total) es la misma en
cada intento. Antes de registrar una orden, y cada vez que STP rechaza una, la orden se busca en ese día y en los
siguientes (`lookup-days`, 4 por defecto), así que un pago cuya respuesta se perdió se sigue en lugar de pagarse dos
veces. El banco receptor sale de los primeros tres dígitos de la CLABE (`40` + código para bancos, `37` para la banca
de desarrollo, `90` para otros participantes). Los nombres y el concepto van sin acentos ni símbolos, en hasta 39
caracteres.

STP solo paga MXN, así que con `stp` una billetera en otra moneda no puede retirar: `GET /api/v1/withdrawals/currencies`
lista las que sí pueden (la app oculta **Retirar** en las demás), y un retiro desde otra responde
`422 WITHDRAWAL_CURRENCY_NOT_SUPPORTED` antes de que salga dinero de la billetera.

| Configuración | Qué es |
|---|---|
| `STP_COMPANY` | El nombre de la empresa de la plataforma en STP (`empresa`) |
| `STP_ACCOUNT` | La CLABE de 18 dígitos de la plataforma en STP, desde la que se pagan los retiros |
| `STP_ACCOUNT_HOLDER`, `STP_ACCOUNT_HOLDER_RFC` | A nombre de quién está esa cuenta, dato que llega al banco receptor |
| `STP_PRIVATE_KEY` | La llave PEM cuyo certificado tiene STP, PKCS#8 sin cifrar (`openssl pkcs8 -topk8 -nocrypt -in key.pem`) |
| `STP_ORDERS_URL`, `STP_QUERY_URL` | El demo de STP por defecto fuera de producción; obligatorias en producción (STP las entrega con la cuenta) |

Los 34 campos firmados, su orden, las longitudes y los códigos de estado coinciden con la documentación de SpeiWS de STP
tal como la implementan los clientes públicos de STP (stpmex para Python, go-stp). Las pruebas corren contra `FakeStp`,
que revisa cada firma. Antes de salir a producción, confírmalos con STP mismo: `StpSandboxCheck` envía una orden real de
1.00 MXN al demo de STP con las credenciales de la cuenta y la vuelve a leer. Nunca corre en el build:

```bash
STP_SANDBOX_CHECK=true STP_COMPANY=... STP_ACCOUNT=... STP_ACCOUNT_HOLDER=... STP_ACCOUNT_HOLDER_RFC=...   STP_PRIVATE_KEY_FILE=/path/key.pem STP_SANDBOX_CLABE=...   ./mvnw test -Dtest=StpSandboxCheck -Dsurefire.failIfNoSpecifiedTests=false
```

Pasa cuando STP acepta la orden; su salida dice lo que STP responde sobre ella. Si STP acepta la orden pero la consulta
nunca la encuentra, revisa con STP `STP_QUERY_URL` y los campos de la consulta.

#### Pagos con Openpay

Con `PAYOUT_PROVIDER=openpay`, los retiros en MXN se pagan a la CLABE a través de Openpay (BBVA): `POST
/v1/{merchant}/payouts` con `method: bank_account`, autenticado con la llave privada como usuario de HTTP Basic. A
diferencia de STP, su sandbox es gratis y no necesita empresa: regístrate en `sandbox-dashboard.openpay.mx` con un
nombre de proyecto, y el id del comercio y la llave privada (`sk_...`) están en el panel.

Cada pago lleva el id del retiro como su `order_id`, que Openpay acepta una sola vez (`409`, error 1006): un intento
cuya respuesta se perdió encuentra el pago ya hecho en lugar de pagar dos veces. El proceso de pagos lo lee por id:
`completed` paga el retiro, `failed` lo hace fallar (`OPENPAY_FAILED`) y devuelve el dinero. Un pago que Openpay
rechaza falla al momento (`OPENPAY_REJECTED_<código>`, por ejemplo 1007 cuando la cuenta bancaria no lo acepta). Uno
que solo tiene que esperar se queda pendiente y se reenvía: Openpay caído, llaves incorrectas (1002), pagos no
habilitados para el comercio (3006) o saldo insuficiente de la plataforma en Openpay (4001). Openpay solo paga pesos,
así que las billeteras en dólares no pueden retirar con él, igual que con STP.

| Configuración | Qué es |
|---|---|
| `OPENPAY_MERCHANT_ID` | El id del comercio, en el panel |
| `OPENPAY_PRIVATE_KEY` | La llave privada (`sk_...`); la pública se rechaza al arrancar |
| `OPENPAY_URL` | El sandbox (`https://sandbox-api.openpay.mx`) por defecto fuera de producción; obligatoria en producción (`https://api.openpay.mx`) |

La petición y sus estados siguen la referencia de la API de Openpay y sus SDK oficiales. Las pruebas corren contra
`FakeOpenpay`. `OpenpaySandboxCheck` lo confirma contra el sandbox real: envía un pago de 1.00 MXN, lo vuelve a enviar
para ver que el `order_id` se acepta una sola vez, y lo vuelve a leer. Nunca corre en el build:

```bash
OPENPAY_SANDBOX_CHECK=true OPENPAY_MERCHANT_ID=... OPENPAY_PRIVATE_KEY=sk_... \
  ./mvnw test -Dtest=OpenpaySandboxCheck -Dsurefire.failIfNoSpecifiedTests=false
```

Si dice que la cuenta no tiene saldo (4001), primero haz un cargo de prueba con tarjeta en el panel del sandbox. Una
cuenta de sandbox nueva responde 3006 ("Not authorized to generate payments") hasta que el soporte de Openpay habilita
los pagos para el comercio; con el proveedor en `openpay`, hasta entonces los retiros se quedan pendientes en lugar de
fallar.

Una cuenta bancaria no recibe retiros durante sus primeras 24 horas (`wallet.accounts.bank-account-cooling-off`):
alguien que tomó el control de una cuenta no puede agregar su propia CLABE y vaciar la billetera antes de que el dueño
lea el correo de "Cuenta bancaria agregada". Hasta entonces, los retiros a ella responden
`422 BANK_ACCOUNT_COOLING_OFF`, y cada cuenta bancaria muestra `withdrawalsAvailableFrom`.

### Dónde está el dinero

El libro mayor sigue al dinero hasta donde realmente está, con estas cuentas del sistema por moneda:

| Cuenta | Tipo | Contiene |
|---|---|---|
| `SETTLEMENT` | activo | El dinero de la plataforma en el banco socio; de ahí se pagan los retiros |
| `PROVIDER_CLEARING` | activo | El dinero que Stripe cobró y todavía no ha depositado al banco |
| `UNAPPLIED_PAYMENTS` | pasivo | El dinero cobrado a un pagador que ninguna billetera ha recibido todavía, o que se está reembolsando |
| `PAYOUTS_IN_TRANSIT` | pasivo | El dinero tomado de las billeteras para pagos que el banco no ha confirmado |
| `PROVIDER_FEES` | gasto | Lo que cobró Stripe |
| `PLATFORM_CAPITAL` | capital | Lo que los dueños pusieron en `SETTLEMENT` |

Un pago exitoso se registra al momento (`PAYMENT_RECEIVED`: en `PROVIDER_CLEARING`, o directo en `SETTLEMENT` con el
proveedor simulado, contra `UNAPPLIED_PAYMENTS`); luego el depósito lo mueve a la billetera, y un reembolso lo
devuelve (`PAYMENT_REFUNDED`). Cada 15 minutos un proceso lee las transacciones de saldo de Stripe y registra lo nuevo:
comisiones (`PROVIDER_FEE`), depósitos al banco (`PROVIDER_PAYOUT`, de clearing a settlement) y depósitos fallidos. Los
cargos y reembolsos que causó la aplicación solo se reconocen; cualquier otra cosa (disputas, ajustes, reembolsos
hechos desde el panel de Stripe) se deja sin conciliar para que la revise una persona. Después, el proceso compara el
saldo de Stripe con `PROVIDER_CLEARING`: la diferencia es la métrica `wallet_provider_balance_difference`, y la alerta
`ProviderBalanceMismatch` se dispara cuando sigue sin cuadrar durante dos horas.

Como Stripe deposita días después de cobrar, mientras tanto los retiros se pagan con capital: un administrador
registra cada transferencia que los dueños envían al banco socio como una aportación de capital. Un pago que el banco
confirma mientras `SETTLEMENT` no alcanza a cubrirlo se queda `PENDING` y se registra cuando llega capital o los
depósitos de Stripe. Del mismo modo, un reembolso mayor a lo que tiene `PROVIDER_CLEARING` (Stripe ya depositó ese
dinero) se registra contra `SETTLEMENT`, porque entonces Stripe lo toma de la cuenta bancaria.

## Transferencias

```http
POST /api/v1/transfers
Authorization: Bearer <accessToken>
Idempotency-Key: 6f1c2a9e-3b7d-4c55-9a0e-2f8b1d4c7e10

{"sourceWalletId": "...", "recipientEmail": "bob@example.com", "amount": 30.25, "description": "Cena"}
```

El destinatario recibe en su billetera de la moneda de quien envía. `GET /api/v1/transfers/{id}` devuelve una
transferencia a quien la envió o a quien la recibió (`direction` es `SENT` o `RECEIVED`, con ambos nombres), y los
movimientos de los dos la muestran con el id de la transferencia como `operationId`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/transfers/recipients?limit=8` | Personas a las que pagaste, las más recientes primero (de 1 a 20), con cuántas veces |
| GET | `/api/v1/transfers/recipient?email=&currency=MXN` | De quién es un correo antes de enviar, como nombre corto (`Ana M. L.`); `422 RECIPIENT_NOT_FOUND` si nadie con ese correo puede recibir en esa moneda. Limitado a 20 consultas por minuto por usuario |

Cada petición que mueve dinero necesita un `Idempotency-Key` (usa un UUID nuevo por cada acción del usuario):

| Situación | Respuesta |
|---|---|
| Primera petición | `201`, `Idempotent-Replayed: false` |
| Reintento con la misma llave y el mismo cuerpo, aunque sea simultáneo | El mismo cuerpo `201`, `Idempotent-Replayed: true`; el dinero se mueve una vez |
| Misma llave, cuerpo distinto | `422 IDEMPOTENCY_KEY_REUSED` |
| La petición falló (por ejemplo, `INSUFFICIENT_FUNDS`) | No se guarda nada; la llave se puede reintentar |

## Límites y administración

- **Límite diario:** los retiros y las transferencias enviadas comparten un límite por billetera y por día local (hora
  de la Ciudad de México): 50,000.00 MXN y 3,000.00 USD por defecto (`wallet.accounts.daily-outgoing-limit`).
  Pasarlo devuelve `422 DAILY_LIMIT_EXCEEDED` con lo que queda para hoy. Los depósitos no tienen límite.
- **Administrador:** se crea al arrancar a partir de `ADMIN_EMAIL` y `ADMIN_PASSWORD` (perfil dev:
  `admin@wallet.local` / `admin-dev-password`). Un usuario existente con ese correo nunca se promueve.
  Antes de usar los endpoints de administración, configura el doble factor como cualquier usuario
  (`/api/v1/users/me/two-factor`) y vuelve a iniciar sesión con un código.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/admin/users` | Crea un administrador o agente de soporte (`{"email", "fullName", "role", "reason"}`). Su dueño recibe por correo su rol, su dirección y una contraseña generada, que nadie más ve y que no se guarda sin hash. Hasta que la reemplaza, `mustChangePassword` es true y todo excepto `GET /api/v1/users/me`, `PUT /api/v1/users/me/language`, `POST /api/v1/users/me/password` y `/api/v1/auth/*` responde `403 PASSWORD_CHANGE_REQUIRED`. `POST /api/v1/auth/password-reset` sigue funcionando si se pierde ese correo |
| GET | `/api/v1/admin/users/signups?days=30` | Clientes que se registraron cada día, hasta 180 días; los días sin nadie no tienen fila |
| GET | `/api/v1/admin/operations/daily?days=30` | Transferencias, depósitos y retiros terminados por día, tipo y moneda, hasta 180 días; los días sin nada no tienen fila |
| GET | `/api/v1/admin/ledger/customer-balances` | Todo el dinero de los clientes por moneda, en unidades mínimas |
| POST | `/api/v1/admin/users/search` | Busca un usuario por correo (`{"email": "..."}`): estado, verificación, bloqueo |
| POST | `/api/v1/admin/users/suggestions` | Vista previa mientras el administrador escribe (`{"text": "..."}`, 2 caracteres o más): hasta 6 usuarios cuyo correo o nombre lo contiene, o cuyo id empieza con él |
| GET | `/api/v1/admin/operations/suggestions` | Hasta 6 transferencias, depósitos y retiros cuyo id empieza con `q`, los más recientes primero; `kind` lo limita a uno |
| GET | `/api/v1/admin/wallets/suggestions` | Hasta 6 billeteras cuyo id empieza con `q`, las más recientes primero |
| GET | `/api/v1/admin/users/{id}` | Lo mismo, por id de usuario |
| GET | `/api/v1/admin/users/{id}/wallets` | Las billeteras del usuario con saldo y estado |
| POST | `/api/v1/admin/users/{id}/unlock` | Quita un bloqueo antes de que expire (`{"reason": "..."}`, obligatorio) |
| POST | `/api/v1/admin/users/{id}/disable` | Bloquea el inicio de sesión y todo movimiento de dinero, cierra todas las sesiones (`{"reason": "..."}`, obligatorio) |
| POST | `/api/v1/admin/users/{id}/enable` | Los vuelve a permitir (`{"reason": "..."}`) |
| POST | `/api/v1/admin/users/{id}/two-factor/reset` | Desactiva el doble factor por un teléfono perdido (`{"reason": "..."}`) |
| GET | `/api/v1/admin/wallets/{id}` | Cualquier billetera con dueño, saldo y estado |
| POST | `/api/v1/admin/wallets/{id}/freeze` | Bloquea todo movimiento de dinero (`{"reason": "..."}`, obligatorio) |
| POST | `/api/v1/admin/wallets/{id}/unfreeze` | Lo vuelve a permitir (`{"reason": "..."}`) |
| GET | `/api/v1/admin/transfers/{id}`, `/deposits/{id}`, `/withdrawals/{id}` | Cualquier operación, con su asiento en el libro mayor |
| GET | `/api/v1/admin/audit-events?targetId=&actorId=&action=&from=&to=&page=&size=` | Bitácora de auditoría, lo más reciente primero; `from` y `to` son instantes, `to` excluido |
| POST | `/api/v1/admin/adjustments` | Abona o carga a mano una billetera, con un motivo y opcionalmente un ticket (`{"walletId", "direction", "amount", "reason", "ticketId"}`). Hasta `adjustment-single-approval-up-to` se aplica al momento; por encima, espera a un segundo administrador |
| GET | `/api/v1/admin/adjustments?status=&ticketId=&page=0&size=20`, `/api/v1/admin/adjustments/{id}` | Los ajustes, y cuántos esperan aprobación |
| POST | `/api/v1/admin/adjustments/{id}/approve` | Un segundo administrador lo aplica; quien lo pidió recibe `ADJUSTMENT_SELF_APPROVAL` |
| POST | `/api/v1/admin/adjustments/{id}/reject` | Lo rechaza con un motivo; quien lo pidió puede retirarlo de esta forma |
| GET | `/api/v1/admin/ledger/system-accounts` | Saldos de las cuentas propias de la plataforma, en unidades mínimas |
| POST | `/api/v1/admin/ledger/capital-contributions` | Registra capital transferido a settlement (`{"currency", "amount", "reference", "reason"}`); cada `reference` bancaria una vez (`409 CAPITAL_CONTRIBUTION_EXISTS`) |
| GET | `/api/v1/admin/payments/provider-transactions?status=&limit=50` | Movimientos de saldo de Stripe, los más recientes primero; todos los estados, o uno de `PENDING`, `POSTED`, `UNMATCHED`, `RESOLVED` |
| POST | `/api/v1/admin/payments/provider-transactions/{provider}/{id}/resolve` | Cierra un movimiento sin conciliar (`{"reason": "..."}`, obligatorio); queda en la auditoría y no registra nada en el libro mayor |
| GET | `/api/v1/admin/payments/provider-balance-differences` | El proveedor en uso, si guarda dinero, y por moneda su saldo frente a `PROVIDER_CLEARING` según la última sincronización |

Todos los endpoints `/api/v1/admin/**` requieren el rol `ADMIN` (si no, `403 FORBIDDEN`) y, fuera de las pruebas, un
administrador con doble factor activo. Los registros, las verificaciones de correo, los cambios de contraseña y las
solicitudes de restablecimiento, los bloqueos y desbloqueos, los cambios de estado de cuentas y billeteras y cada
movimiento de dinero se escriben en una bitácora de auditoría de solo inserción a través del outbox de eventos de
Spring Modulith, así que no se pierde ninguna entrada aunque la app se caiga justo después de una operación.

Una cuenta deshabilitada responde `403 ACCOUNT_DISABLED` al iniciar sesión, y sus tokens de renovación dejan de
funcionar. Los tokens de acceso emitidos antes siguen sirviendo para lecturas hasta que expiran (15 minutos como
máximo), pero cada depósito, retiro o transferencia revisa la cuenta con un bloqueo, así que ninguno pasa después de
que se confirma la deshabilitación. La cuenta del administrador no se puede deshabilitar.

## Chat de soporte

Un usuario escribe a `/api/v1/support/conversation/messages` y el bot responde desde los archivos Markdown de
`src/main/resources/support/knowledge/<idioma>/`, en el idioma del usuario. No hay IA y nada sale de la app: compara
palabras, sin importar mayúsculas, acentos, terminaciones ni errores de tecleo, y les da peso: una palabra de la que
tratan pocas secciones cuenta más, y una frase clave encontrada completa cuenta todavía más. Solo responde cuando una
sección gana claramente, y si no, ofrece las más cercanas. Las palabras que significan lo mismo van en
`reply: synonyms`, como `dinero: plata, lana`. Editar lo que sabe es editar esos archivos y desplegar.
Cada encabezado `##` es una respuesta, escrita como la preguntaría la gente:

```markdown
## ¿Cuánto tarda un retiro?
keywords: retiro, sacar, llegar
escalate: true

El texto de la respuesta.
```

`keywords` son otras formas de preguntarlo. `escalate: true` además pasa la conversación a una persona, para lo que el
bot puede explicar pero no resolver, y `small-talk: true` marca los saludos, que no son respuestas a calificar.

Soporte es para problemas, y las preguntas sobre la app son para el bot, así que lo que hace sin una respuesta depende de
lo que se escribió. Lo que no puede leer ("k", "asdf", una sola palabra desconocida) pide que se escriba de nuevo
(`reply: unclear`), y una palabra que no puede leer entre otras que sí se ignora. Cuando solo coincide parte de un
mensaje, el mensaje lleva `suggestions`, los títulos de hasta 3 secciones, y el texto de `reply: suggestions`; enviar un
título como mensaje da esa sección. Una oración que puede leer pero de la que no sabe nada recibe el texto de
`reply: not-found`, que pide decirlo de otra forma. Cada idioma necesita los tres, o la app no arranca.

Una conversación pasa a una persona cuando un mensaje usa una de las palabras clave de `reply: not-found` ("problema",
"queja"), cuando el bot sigue sin saber después de pedir que se diga de otra forma, cuando el usuario lo pide o dice
que la respuesta no le ayudó, o cuando envía un archivo. Va a un usuario `SUPPORT` activo sin otro caso, el que lleve
más tiempo esperando, o si no, espera en la cola hasta que uno se libere. El agente recibe una notificación que abre el
caso con todo lo dicho hasta entonces. La base de datos misma mantiene una conversación abierta por usuario y un caso
activo por agente. Los casos regresan a la cola si su agente se deshabilita o deja de serlo.

Un caso cuyos mensajes antes de llegar a la cola hablan de dinero (un depósito, retiro, cargo, saldo, SPEI, reembolso,
fraude…, en cualquiera de los dos idiomas) tiene prioridad `HIGH` y va antes que los demás; dentro de una prioridad, va
primero quien lleva más tiempo esperando. Un agente puede pausar los casos nuevos y conservar el que está atendiendo
(`availability`). A un cliente que deja de responderle al agente se le avisa después de
`wallet.support.inactivity.warn-after` (15 minutos) que el caso se cerrará, y se cierra como `NO_REPLY` después de
otros `close-after` (15 minutos) si sigue sin decir nada, lo que libera al agente.

Un agente cierra un caso con un motivo: `SOLVED`, `INFORMATION`, `TICKET`, `NO_REPLY`, `DUPLICATE` u `OTHER` (`SOLVED`
si no da ninguno); uno que cierra el cliente queda como `CUSTOMER_CLOSED`. Mientras lo atiende, ve los casos anteriores
del cliente y deja notas para el equipo, que el cliente nunca ve. Cuando respondió algo que debió responder el bot, le
sugiere la pregunta y la respuesta a los administradores, que la agregan a los archivos de conocimiento y la marcan
`ADDED`, o `DISMISSED`. Los administradores también ven dónde falló el bot: `NOT_HELPED` (el usuario dijo que la
respuesta no le ayudó), `NOT_FOUND`, `SUGGESTED` (solo ofreció secciones cercanas) y, solo si se pide, `ASKED_AGAIN`
(lo que no pudo leer), con la misma pregunta contada una vez y las más repetidas primero.

Lo que un agente escala se sigue en su propia lista, desde cualquiera de sus casos, y tanto el cliente como ese agente
reciben un aviso cuando un administrador lo resuelve.

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/api/v1/support/conversation` | La conversación abierta, o la cerrada que espera su encuesta; `204` si no hay ninguna |
| POST | `/api/v1/support/conversation/messages` | `{"text": "..."}`, hasta 2,000 caracteres; empieza una conversación si hace falta |
| POST | `/api/v1/support/conversation/attachments` | `file` multipart y `text` opcional: JPEG, PNG, WebP, PDF o MP4 de hasta 20 MB, revisado por sus bytes |
| POST | `/api/v1/support/conversation/escalate` | Pide una persona |
| POST | `/api/v1/support/conversation/close` | La cierra; si la atendió un agente, la respuesta la deja esperando su encuesta |
| POST | `/api/v1/support/conversation/survey` | `{"score": 5, "comment": "..."}`, de 1 a 5 |
| GET | `/api/v1/support/attachments/{id}` | Descarga un archivo de una conversación en la que participas |
| GET | `/api/v1/support/agent/summary` | `SUPPORT`: el caso que se está atendiendo, si te llegan casos nuevos, y la cola (cuántos esperan, cuántos son urgentes, la espera más larga) |
| PUT | `/api/v1/support/agent/availability` | `SUPPORT`: `{"available": false}` pausa los casos nuevos y conserva el que se está atendiendo; `true` los vuelve a tomar |
| GET | `/api/v1/support/agent/metrics?days=30` | `SUPPORT`: cómo te fue con los casos que recibiste en los últimos `days` (de 1 a 365): casos, cerrados, tiempo a la primera respuesta y al cierre, encuestas y su promedio |
| GET | `/api/v1/support/agent/conversations?status=&q=&page=0&size=20` | `SUPPORT`: tus casos, el abierto primero; `status` `ASSIGNED` o `RESOLVED`, `q` el nombre del cliente o el folio |
| GET | `/api/v1/support/agent/conversations/{id}` | `SUPPORT`: uno de tus casos, incluida la parte del bot. Los agentes ven el nombre del usuario, no su correo ni su dinero |
| POST | `/api/v1/support/agent/conversations/{id}/messages` | `SUPPORT`: responde |
| POST | `/api/v1/support/agent/conversations/{id}/attachments` | `SUPPORT`: envía un archivo |
| POST | `/api/v1/support/agent/conversations/{id}/resolve` | `SUPPORT`: lo cierra con un motivo (`{"reason": "INFORMATION"}`, opcional), le pide la encuesta al usuario y toma el siguiente de la cola |
| GET | `/api/v1/support/agent/conversations/{id}/history` | `SUPPORT`: los casos anteriores del cliente con personas, el más reciente primero |
| GET, POST | `/api/v1/support/agent/conversations/{id}/notes` | `SUPPORT`: las notas del equipo sobre el caso, y agregar una (`{"text": "..."}`, hasta 1,000 caracteres) |
| POST | `/api/v1/support/agent/conversations/{id}/bot-suggestions` | `SUPPORT`: propone que el bot responda una pregunta por sí mismo (`{"question", "answer"}`), en el idioma del cliente |
| GET | `/api/v1/support/agent/conversations/{id}/customer` | `SUPPORT`: la situación del cliente, incluida la verificación de identidad |
| GET | `/api/v1/support/agent/conversations/{id}/operations`, `/tickets` | `SUPPORT`: las últimas operaciones del cliente, y los tickets del caso |
| POST | `/api/v1/support/agent/conversations/{id}/tickets` | `SUPPORT`: pasa un problema a los administradores, sobre una operación o no (`category`: `OPERATION`, `ACCOUNT_ACCESS`, `IDENTITY`, `OTHER`) |
| POST | `/api/v1/support/agent/conversations/{id}/release` | `SUPPORT`: devuelve el caso a la cola |
| POST | `/api/v1/support/agent/conversations/{id}/transfer` | `SUPPORT`: lo pasa a un compañero libre (`{"agentId": "..."}`) |
| GET | `/api/v1/support/agent/colleagues` | `SUPPORT`: a quién se le puede pasar |
| GET | `/api/v1/support/agent/tickets?status=&page=0&size=20` | `SUPPORT`: lo que escalaste desde cualquiera de tus casos, los abiertos del más antiguo al más nuevo, con el nombre del cliente y cuántos están abiertos; `status` `OPEN` o `RESOLVED` |
| GET, POST | `/api/v1/support/agent/replies` | `SUPPORT`: las respuestas guardadas, y guardar otra |
| DELETE | `/api/v1/support/agent/replies/{id}` | `SUPPORT`: borra una |
| GET | `/api/v1/admin/support/tickets?status=&page=0&size=20`, `/api/v1/admin/support/tickets/{id}` | Lo que pasaron los agentes, y cuántos están abiertos |
| POST | `/api/v1/admin/support/tickets/{id}/resolve` | Cierra uno con lo que se hizo |
| GET | `/api/v1/admin/support/summary` | Conversaciones por estado, la cola, cómo terminaron (por motivo) y la calificación promedio de las encuestas |
| GET | `/api/v1/admin/support/conversations?status=&q=&page=0&size=20` | Las que llegaron a una persona o a la cola, las más recientes primero; `q` el nombre o correo de un cliente, o un folio |
| GET | `/api/v1/admin/support/conversations/{id}` | Una de ellas con su encuesta |
| GET | `/api/v1/admin/support/agents?days=30` | Cada agente activo y cómo le fue con los casos que recibió en los últimos `days` |
| GET | `/api/v1/admin/support/bot/summary?days=30` | Lo que hizo el bot con las preguntas de los últimos `days` |
| GET | `/api/v1/admin/support/bot/misses?kind=&language=&days=30&page=0&size=20` | Las preguntas en las que falló, las más repetidas primero (`kind` y `language` opcionales) |
| GET | `/api/v1/admin/support/bot/suggestions?status=` | Las sugerencias de los agentes, las abiertas de la más antigua a la más nueva (`OPEN`, `ADDED` o `DISMISSED`) |
| POST | `/api/v1/admin/support/bot/suggestions/{id}/review` | `{"status": "ADDED"}` o `DISMISSED` |
| POST | `/api/v1/admin/users/{id}/role` | `{"role": "SUPPORT", "reason": "..."}` o `USER`; cierra las sesiones de la cuenta y queda en la auditoría |

## Notificaciones

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/notifications?unreadOnly=false&page=0&size=20` | Tus notificaciones, las más recientes primero |
| GET | `/api/v1/notifications/unread-count` | `{"count": 3}`, lo bastante barato para consultarlo seguido para un contador |
| POST | `/api/v1/notifications/{id}/read` | Marca una como leída (`204`); repetirlo conserva la primera hora de lectura |
| POST | `/api/v1/notifications/read-all` | Marca todas como leídas (`204`) |
| GET | `/api/v1/notifications/preferences` | Qué avisos llegan también por correo: `emailMoneyIn`, `emailMoneyOut` y `emailSignIns` (todos activos por defecto) |
| PUT | `/api/v1/notifications/preferences` | Los cambia (`{"emailMoneyIn": true, "emailMoneyOut": false, "emailSignIns": true}`); `emailSignIns` es opcional y, si se omite, conserva su valor |
| GET | `/api/v1/notifications/stream` | Eventos enviados por el servidor: `ready` al conectarse y luego `notification` (`{id, type, title}`) conforme llega cada una. Se cierra a los 5 minutos (reconecta con un token vigente); un comentario cada 25 segundos evita que los proxies lo cierren |

Los usuarios reciben un aviso cuando se registran, cuando los intentos fallidos bloquean su cuenta, cuando se cambia o
restablece su contraseña, cuando un administrador deshabilita o habilita su cuenta, en cada depósito, retiro y
transferencia (enviada y recibida), cuando se reembolsa un depósito pagado porque la billetera no pudo recibirlo,
cuando el banco rechaza un retiro, cuando se activa o desactiva el doble factor, cuando un administrador congela o
descongela una de sus billeteras, cuando se agrega o borra una cuenta bancaria, cuando cambia su teléfono, cuando un
administrador quita un bloqueo y cuando se cierran sesiones desde los dispositivos de la cuenta. Soporte tiene los
suyos: un agente se entera de un caso que se le asigna y de un ticket suyo resuelto; un cliente, de que un agente se
une o responde, de que el caso se cierra y de que su ticket se resolvió. Cada uno también se envía por correo, salvo
que el usuario haya apagado el correo para el dinero recibido (depósitos, transferencias recibidas) o enviado
(transferencias enviadas, retiros); los avisos de seguridad y las operaciones fallidas siempre se envían por correo, y
la bandeja lo guarda todo. Cada inicio de sesión exitoso también se avisa por correo (su fecha y hora, con un enlace a
las sesiones en la configuración), pero solo por correo, para que la bandeja no se llene con los propios inicios de
sesión del usuario; `emailSignIns` lo apaga. La preferencia se lee al enviar el correo, así que apagarla también
detiene los correos que siguen esperando en el outbox. El flujo es por instancia: con varias instancias, cada una solo
llega a las apps conectadas a ella, y las demás ven la notificación en su siguiente consulta. Las notificaciones salen
del outbox de eventos igual que la bitácora de auditoría, así que aparecen un momento después de la operación y nunca
se pierden; el correo es un paso aparte, así que una caída del servidor de correo solo lo retrasa. Las notificaciones
se borran a los 90 días.

Cada correo tiene un diseño según su motivo (`EmailLayout`: tablas y estilos en línea, como lo necesitan los clientes
de correo) más una versión en texto plano, y se escribe en el idioma del usuario. Sus textos viven en
`emails_es.properties` y `emails_en.properties`, y una prueba revisa que los dos tengan los mismos textos. El idioma se
guarda en la cuenta: empieza como el `Accept-Language` de la solicitud de registro y sigue a
`PUT /api/v1/users/me/language`. Un restablecimiento de contraseña o un enlace de verificación pedido al iniciar sesión
usa el `Accept-Language` de la petición sin cambiar el de la cuenta, porque quien lo pide puede no ser el dueño. Las
notificaciones guardan sus cifras en `params`, así que el correo no se limita al texto en inglés que se guarda en la
bandeja. `mvn test` deja todos los diseños en `target/email-previews/` para abrirlos en un navegador.

| Configuración | Valores |
|---|---|
| `wallet.notifications.email.delivery` | `smtp` (dev, pruebas y prod por defecto) o `log` (sin correo, solo una línea en el log) |
| `wallet.notifications.email.app-url` | La app cliente que abre los enlaces de los correos de verificación y restablecimiento |
| `wallet.notifications.email.time-zone` | Zona en la que se muestran las horas en los correos; `America/Mexico_City` por defecto |
| Producción (perfil `prod`) | `MAIL_USERNAME`, `MAIL_PASSWORD`, `EMAIL_FROM`, `APP_URL`; `MAIL_HOST` (`smtp.gmail.com` por defecto) y `MAIL_PORT` (587) solo para usar otro servidor; `EMAIL_DELIVERY=log` apaga el correo |
| Gmail (dev y prod) | El servidor por defecto (`smtp.gmail.com`, puerto 587): `MAIL_USERNAME` y una contraseña de aplicación como `MAIL_PASSWORD` (ver `.env.example`); dev los lee de `.env`. Gmail envía desde `MAIL_USERNAME` y permite unos 500 correos al día. Las pruebas de integración siguen usando Mailpit con Testcontainers |
| Pagos en producción | `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` (el secreto de firma del endpoint del webhook en el panel de Stripe); `PAYMENT_PROVIDER=simulated` para demos sin dinero real |
| Retiros en producción | `PAYOUT_PROVIDER` es obligatorio: `stp` paga los retiros en MXN por SPEI y necesita las variables `STP_*` (ver *Pagos SPEI con STP*); `openpay` los paga a través de Openpay y necesita las `OPENPAY_*` (ver *Pagos con Openpay*); `simulated` no envía dinero real y es solo para demos. Producción no arranca hasta que se define a propósito |

## Operación

### Eventos fallidos

Si un listener de eventos falla (auditoría, notificaciones o correo), su publicación se queda en el outbox y un proceso
la reintenta cada 30 segundos una vez que pasa su espera: 1 minuto después del primer fallo, luego 2, 4... hasta 1 hora.
Las publicaciones atoradas por 10 minutos, por ejemplo porque murió una instancia, se reintentan igual. La métrica
`wallet_events_pending` y su alerta muestran cuándo algo está atorado.

### Conciliación del libro mayor

Cada hora un proceso revisa que cada saldo en caché sea igual a la suma de sus asientos, que cada asiento del diario
cuadre y que, por moneda, los activos más los gastos sean iguales a los pasivos más el capital. Lee una sola foto
consistente, así que nunca bloquea ni lee mal las transferencias en curso, y solo una instancia lo corre a la vez.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/admin/reconciliations?limit=20` | Las últimas corridas, las más recientes primero, con sus diferencias si las hay |
| POST | `/api/v1/admin/reconciliations` | Corre una ahora (`409 RECONCILIATION_IN_PROGRESS` si ya hay una corriendo) |

Los procesos nocturnos también borran las familias de tokens de renovación expiradas, los tokens de enlaces de correo
expirados, las llaves de idempotencia de más de 24 horas, las filas de uso diario de más de 7 días, las
notificaciones de más de 90 días y los ids de webhooks de pago de más de 30 días. Cada 5 minutos un proceso liquida
los pagos cuyo webhook del proveedor nunca llegó, cada 15 minutos otro sigue el saldo de Stripe, y cada minuto otro
avanza los retiros pendientes.

### Límites de peticiones

| Peticiones | Límite | Contado por |
|---|---|---|
| `POST /api/v1/auth/password-reset` | 5 cada 15 minutos | dirección del cliente |
| `POST /api/v1/auth/register` | 15 cada 15 minutos | dirección del cliente |
| `POST /api/v1/users/me/email-verification` | 3 cada 15 minutos | usuario |
| Cualquier otro `POST /api/v1/auth/**` | 20 por minuto | dirección del cliente |
| `POST` de depósitos, retiros y transferencias | 30 por minuto | usuario |
| `POST /api/v1/payments/webhooks/**` | 600 por minuto | dirección del cliente |
| `GET /api/v1/transfers/recipient` | 20 por minuto | usuario |
| Cualquier otra petición `/api/**` | 300 por minuto | usuario |

Al pasar el límite, la API responde `429 RATE_LIMITED` con `Retry-After` (en segundos); las respuestas limitadas
incluyen `X-RateLimit-Remaining`. Los límites se configuran en `wallet.rate-limit` y aplican por instancia. Detrás de
un balanceador de carga, define `SERVER_FORWARD_HEADERS_STRATEGY=native` para que los límites vean la dirección del
cliente.

### Métricas, trazas y tableros

```bash
docker compose --profile observability up -d
```

| Herramienta | URL | Qué muestra |
|---|---|---|
| Grafana | http://localhost:3030 | Tablero "Digital Wallet": tráfico, latencia, dinero movido, rechazos, salud del libro mayor y del outbox, pagos pendientes y rechazados, JVM y pool de la base de datos (inicia sesión como `admin`/`admin` para editar) |
| Prometheus | http://localhost:9090 | Métricas en crudo y reglas de alerta (`docker/observability/prometheus/alerts.yml`) |
| Tempo | desde Grafana, Explore > Tempo | Trazas de cada petición hasta cada sentencia SQL |

En el puerto de administración, health e info están abiertos para las sondas del orquestador; las métricas y los
detalles de los módulos necesitan HTTP Basic `prometheus:<MANAGEMENT_SCRAPE_PASSWORD>` cuando esa variable tiene
valor, cosa que el perfil prod exige. El stack de compose usa `scrape-dev-password` en ambos lados
(`docker/observability/prometheus/prometheus.yml`).

La app arrancada desde el IDE se lee en el puerto 8081. Envía trazas a Tempo en `localhost:4318` solo cuando se
arranca con `TRACE_EXPORT=true` (ponlo en la configuración de ejecución mientras el stack esté arriba): sin Tempo,
cada envío registraría un error de conexión. El `X-Correlation-Id` de cada respuesta también está en su traza
(`correlation.id`), y las líneas del log muestran `[correlationId,traceId,spanId]`. En los paneles de latencia, los
puntos son exemplars: haz clic en uno para abrir la traza de esa petición.

## Pruebas

```bash
./mvnw verify
```

Las pruebas de integración levantan un PostgreSQL y un Mailpit reales con Testcontainers, así que Docker debe estar
corriendo.

## Formato de errores

Cada error sigue RFC 9457 Problem Details más un `code` estable y el `correlationId` de la petición:

```json
{
  "type": "about:blank",
  "title": "Business rule violation",
  "status": 422,
  "detail": "Not enough balance",
  "code": "INSUFFICIENT_FUNDS",
  "correlationId": "5f0c..."
}
```

Envía un encabezado `X-Correlation-Id` para seguir una petición en los logs; si no, se genera uno.
