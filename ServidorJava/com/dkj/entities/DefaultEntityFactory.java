package com.dkj.entities;

import com.dkj.model.Height;
import com.dkj.model.LianaId;
import com.dkj.model.Points;
import com.dkj.model.Speed;

/**
 * Fabrica de entidades del juego (Factory Method pattern).
 * 
 * Centraliza la creacion de todas las entidades del juego (cocodrilos y frutas)
 * encapsulando la logica de inicializacion y configuracion. Permite crear
 * instancias de entidades con diferentes configuraciones segun el contexto.
 * 
 * Tipos de entidades:
 * - CrocodileBlue: Enemigo azul que camina en plataformas y desciende por lianas
 * - CrocodileRed: Enemigo rojo que patrulla verticalmente en lianas
 * - Fruit: Coleccionables que otorgan puntos
 * 
 * Modos de spawneo:
 * - Automatico: Spawneado por el motor del juego (ej: cocodrilos azules desde Mario)
 * - Manual: Spawneado por comandos de administrador con parametros especificos
 * 
 * Patron aplicado: Factory Method (GoF)
 */
public final class DefaultEntityFactory {

    /**
     * Crea un cocodrilo azul en modo caminando en plataforma (spawner automatico).
     * 
     * El cocodrilo comienza en la posicion de Mario (x=140) y se mueve hacia
     * una liana aleatoria para luego descender.
     * 
     * @param platformHeight Altura de la plataforma donde aparece
     * @param speed Velocidad de movimiento del cocodrilo
     * @return Nuevo cocodrilo azul en modo WALKING_ON_PLATFORM
     */
    public CrocodileBlue newBlue(Height platformHeight, Speed speed) {
        // Empieza desde donde está Mario (DK está en x=80, Mario en x=140)
        return new CrocodileBlue(platformHeight, speed, Float.valueOf(140.0f));
    }

    /**
     * Crea un cocodrilo azul directamente en una liana bajando (admin console).
     * 
     * El cocodrilo aparece inmediatamente en la liana especificada en modo
     * descendente, utilizado para spawneo manual por administradores.
     * 
     * @param liana Identificador de la liana donde aparece
     * @param height Altura inicial en la liana
     * @param speed Velocidad de descenso
     * @return Nuevo cocodrilo azul en modo DESCENDING_LIANA
     */
    public CrocodileBlue newBlueOnLiana(LianaId liana, Height height, Speed speed) {
        return new CrocodileBlue(liana, height, speed);
    }

    /**
     * Crea un cocodrilo rojo que patrulla una liana verticalmente.
     * 
     * El cocodrilo se mueve entre los limites verticales especificados,
     * cambiando de direccion al alcanzar cualquiera de ellos.
     * 
     * @param liana Identificador de la liana a patrullar
     * @param height Altura inicial en la liana
     * @param speed Velocidad de movimiento vertical
     * @param minHeight Limite inferior de patrullaje (pixeles)
     * @param maxHeight Limite superior de patrullaje (pixeles)
     * @return Nuevo cocodrilo rojo configurado con su ruta de patrullaje
     */
    public CrocodileRed newRed(final LianaId liana, final Height height, final Speed speed, 
                               final Float minHeight, final Float maxHeight) {
        return new CrocodileRed(liana, height, speed, minHeight, maxHeight);
    }

    /**
     * Crea una fruta coleccionable en la posicion especificada.
     * 
     * @param liana Identificador de la liana donde aparece
     * @param height Altura en la liana
     * @param points Valor en puntos que otorga al ser recolectada
     * @return Nueva fruta lista para ser recolectada
     */
    public Fruit newFruit(final LianaId liana, final Height height, final Points points) {
        return new Fruit(liana, height, points);
    }
}