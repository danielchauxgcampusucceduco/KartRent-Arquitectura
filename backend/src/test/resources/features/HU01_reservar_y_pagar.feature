# language: es
Característica: HU-01 Reservar y pagar una tanda en línea
  Como piloto aficionado
  Quiero reservar cupos en una tanda y pagarlos en línea
  Para asegurar mi turno sin hacer fila en el kartódromo

  Antecedentes:
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-1800" de karts ADULTO con 4 cupos
    Y el cliente "1085000001" de 24 años con exoneración firmada

  Escenario: Reserva y pago exitoso con factura electrónica
    Cuando el cliente "1085000001" reserva 2 cupos en la tanda "T-1800"
    Entonces la tanda "T-1800" tiene 2 cupos disponibles
    Cuando paga la reserva con "TARJETA" y la clave "pago-1"
    Entonces la reserva queda "CONFIRMADA"
    Y se emite una factura por 90000 pesos con CUFE

  Escenario: El pago es idempotente (no se cobra dos veces)
    Cuando el cliente "1085000001" reserva 1 cupos en la tanda "T-1800"
    Y paga la reserva con "TARJETA" y la clave "repetida"
    Y paga la reserva con "TARJETA" y la clave "repetida"
    Entonces la pasarela registró 1 cobro

  Escenario: RN-01 la reserva no pagada expira y libera los cupos
    Cuando el cliente "1085000001" reserva 3 cupos en la tanda "T-1800"
    Y pasan 11 minutos
    Y se ejecuta la expiración de reservas
    Entonces la reserva queda "EXPIRADA"
    Y la tanda "T-1800" tiene 4 cupos disponibles

  Escenario: No se venden más cupos de los disponibles
    Cuando el cliente "1085000001" intenta reservar 5 cupos en la tanda "T-1800"
    Entonces se rechaza con el mensaje "Cupos insuficientes"

  Escenario: RN-03 sin exoneración firmada no puede reservar
    Dado el cliente "1085000009" de 30 años sin exoneración firmada
    Cuando el cliente "1085000009" intenta reservar 1 cupos en la tanda "T-1800"
    Entonces se rechaza indicando "exoneración"
