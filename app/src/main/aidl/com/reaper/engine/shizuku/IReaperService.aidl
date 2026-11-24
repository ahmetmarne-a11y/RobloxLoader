package com.reaper.engine.shizuku;

interface IReaperService {
    // Delta'ya script inject et
    void injectScript(String scriptContent);
    // Roblox PID döndür
    int getRobloxPid();
    // Servis hazır mı
    boolean isReady();
    void destroy();
}
