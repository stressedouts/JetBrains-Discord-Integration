package com.almightyalpaca.jetbrains.plugins.discord.utils;


import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class SerializablePair<L extends Serializable, R extends Serializable> extends Pair<L, R> implements Serializable
{
    public SerializablePair(L first, R second)
    {
        super(first, second);
    }
}
