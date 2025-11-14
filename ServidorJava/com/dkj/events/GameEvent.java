package com.dkj.events;

public sealed interface GameEvent permits StateEvent, ScoreEvent, DeathEvent, LevelEvent {}
