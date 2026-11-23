# language: es
Característica: HU-03 Cronometraje en vivo
  Como piloto en pista
  Quiero ver mis tiempos por vuelta en la pantalla en tiempo real
  Para comparar mi rendimiento con el de los demás pilotos

  Antecedentes:
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-2000" de karts ADULTO con 4 cupos
    Y la flota tiene los karts adultos 1 a 6 con transponder
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y el cliente "1085000002" de 27 años con exoneración firmada
    Y el cliente "1085000001" tiene 1 cupos pagados en la tanda "T-2000"
    Y el cliente "1085000002" tiene 1 cupos pagados en la tanda "T-2000"
    Y el piloto "1085000001" corre en el kart 1 y el piloto "1085000002" en el kart 2 en la tanda "T-2000" iniciada

  Escenario: Se calcula el tiempo de vuelta a partir de dos pasos por meta
    Cuando el transponder "TR-101" pasa por meta en "23:03:10.250"
    Y el transponder "TR-101" pasa por meta en "23:03:42.870"
    Entonces se registra una vuelta de 32620 milisegundos

  Escenario: RN-07 una lectura menor a 25 segundos se descarta
    Cuando el transponder "TR-101" pasa por meta en "23:03:10.250"
    Y el transponder "TR-101" pasa por meta en "23:03:20.000"
    Entonces no se registra ninguna vuelta

  Escenario: RN-08 la clasificación se ordena por mejor vuelta
    Cuando el transponder "TR-101" pasa por meta en "23:03:00.000"
    Y el transponder "TR-102" pasa por meta en "23:03:01.000"
    Y el transponder "TR-101" pasa por meta en "23:03:35.000"
    Y el transponder "TR-102" pasa por meta en "23:03:31.500"
    Entonces el líder de la tanda "T-2000" es el kart 2
