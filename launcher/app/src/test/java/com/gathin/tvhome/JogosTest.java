package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JogosTest {
    @Test
    public void pastaENucleosDoLauncher() {
        assertEquals("/storage/emulated/0/Android/media/com.gathin.tvhome/Jogos", Jogos.PASTA);
        assertEquals("libsnes9x.so", Jogos.Sistema.SNES.nucleo);
        assertEquals("libfbneo.so", Jogos.Sistema.FLIPERAMA.nucleo);
        assertEquals("libpcsx_rearmed.so", Jogos.Sistema.PS1.nucleo);
        assertEquals("jogos.json", Jogos.JSON);
    }
}
