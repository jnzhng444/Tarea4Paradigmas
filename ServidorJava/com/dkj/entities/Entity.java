package com.dkj.entities;

import com.dkj.model.Position;


public sealed interface Entity permits CrocodileRed, CrocodileBlue, Fruit {
    Position position();
}
