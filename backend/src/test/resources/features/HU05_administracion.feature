# language: es
Característica: HU-05 Configurar la operación y consultar la ocupación
  Como administrador del kartódromo
  Quiero gestionar tarifas, tandas y el umbral de mantenimiento, y ver la ocupación
  Para tomar decisiones comerciales con datos

  Escenario: No se puede programar una tanda sin tarifa vigente
    Cuando el administrador intenta programar la tanda "T-X" de karts INFANTIL
    Entonces se rechaza indicando "tarifa"

  Escenario: RN-13 la ocupación es cupos vendidos sobre cupos ofrecidos
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-2200" de karts ADULTO con 4 cupos
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y el cliente "1085000001" tiene 2 cupos pagados en la tanda "T-2200"
    Cuando el administrador consulta el reporte de ocupación del mes
    Entonces la ocupación total es 50 por ciento
    Y los ingresos confirmados son 90000 pesos

  Escenario: El administrador cambia el umbral de mantenimiento
    Cuando el administrador fija el umbral de mantenimiento en 25 horas
    Entonces el umbral vigente es 25 horas
