# language: es
Característica: HU-02 Asignar karts y armar la parrilla
  Como jefe de boxes
  Quiero asignar un kart disponible a cada piloto confirmado
  Para sacar la tanda a pista a tiempo y con karts en buen estado

  Antecedentes:
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-1900" de karts ADULTO con 4 cupos
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y la flota tiene los karts adultos 1 a 6 con transponder
    Y el cliente "1085000001" tiene 1 cupos pagados en la tanda "T-1900"

  Escenario: Asignación correcta e inicio de la tanda
    Cuando el jefe de boxes asigna el kart 3 al piloto "1085000001" en la tanda "T-1900"
    Y inicia la tanda "T-1900"
    Entonces la tanda "T-1900" queda "EN_CURSO"
    Y el kart 3 queda "EN_PISTA"

  Escenario: RN-04 un kart en mantenimiento no se puede asignar
    Dado el kart 6 está en mantenimiento
    Cuando el jefe de boxes intenta asignar el kart 6 al piloto "1085000001" en la tanda "T-1900"
    Entonces se rechaza con el mensaje "Kart 6 no disponible"

  Escenario: No se puede iniciar una tanda sin parrilla
    Cuando el jefe de boxes intenta iniciar la tanda "T-1900"
    Entonces se rechaza indicando "parrilla"

  Escenario: Retirar un kart de la parrilla lo deja disponible y reabre la asignación
    Cuando el jefe de boxes asigna el kart 3 al piloto "1085000001" en la tanda "T-1900"
    Y el jefe de boxes retira el kart 3 de la tanda "T-1900"
    Entonces la tanda "T-1900" queda "PROGRAMADA"
    Y el kart 3 queda "DISPONIBLE"
    Cuando el jefe de boxes asigna el kart 4 al piloto "1085000001" en la tanda "T-1900"
    Entonces el kart 4 queda "EN_PISTA"

  Escenario: No se retira un kart de una tanda que ya inició
    Cuando el jefe de boxes asigna el kart 3 al piloto "1085000001" en la tanda "T-1900"
    Y inicia la tanda "T-1900"
    Y el jefe de boxes intenta retirar el kart 3 de la tanda "T-1900"
    Entonces se rechaza indicando "parrilla"

  Escenario: Una reserva con kart asignado no se puede cancelar
    Cuando el jefe de boxes asigna el kart 3 al piloto "1085000001" en la tanda "T-1900"
    Y el cliente "1085000001" intenta cancelar su reserva en la tanda "T-1900"
    Entonces se rechaza indicando "asignados"
    Y la reserva queda "CONFIRMADA"
