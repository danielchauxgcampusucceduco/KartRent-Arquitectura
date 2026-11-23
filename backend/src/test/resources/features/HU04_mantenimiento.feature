# language: es
Característica: HU-04 Mantenimiento preventivo por horas de motor
  Como mecánico jefe
  Quiero que el sistema bloquee y genere una orden cuando un kart supera el umbral de horas
  Para evitar fallas en pista y alargar la vida útil de los motores

  Antecedentes:
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-2100" de karts ADULTO con 4 cupos
    Y la flota tiene los karts adultos 1 a 6 con transponder
    Y el kart 3 lleva "19.9" horas desde el último servicio
    Y el mecánico "M-02" trabaja en el taller
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y el cliente "1085000001" tiene 1 cupos pagados en la tanda "T-2100"

  Escenario: RN-09 al superar el umbral se abre una orden y se bloquea el kart
    Dado el piloto "1085000001" corre en el kart 3 en la tanda "T-2100" iniciada
    Cuando pasan 10 minutos
    Y finaliza la tanda "T-2100"
    Entonces se genera 1 orden preventiva para el kart 3
    Y el kart 3 queda "EN_MANTENIMIENTO"

  Escenario: RN-10 solo el mecánico asignado cierra la orden y el kart vuelve a estar disponible
    Dado el piloto "1085000001" corre en el kart 3 en la tanda "T-2100" iniciada
    Y pasan 10 minutos
    Y finaliza la tanda "T-2100"
    Cuando se asigna la orden del kart 3 al mecánico "M-02"
    Y el mecánico "M-01" intenta cerrar la orden del kart 3
    Entonces se rechaza indicando "mecánico asignado"
    Cuando el mecánico "M-02" cierra la orden del kart 3 con "Cambio de bujía y aceite"
    Entonces el kart 3 queda "DISPONIBLE"
    Y el kart 3 tiene 0 horas desde el último servicio

  Escenario: Un kart por debajo del umbral no genera orden
    Dado el piloto "1085000001" corre en el kart 1 en la tanda "T-2100" iniciada
    Cuando pasan 10 minutos
    Y finaliza la tanda "T-2100"
    Entonces se generan 0 órdenes preventivas
