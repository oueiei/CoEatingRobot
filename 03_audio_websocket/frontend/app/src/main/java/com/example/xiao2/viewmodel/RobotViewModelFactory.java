package com.example.xiao2.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.xiao2.repository.DataRepository;
import com.example.xiao2.util.CustomAudioManager;
import com.example.xiao2.util.WebSocketHandler;
import com.nuwarobotics.service.agent.NuwaRobotAPI;

public class RobotViewModelFactory implements ViewModelProvider.Factory {
    private final NuwaRobotAPI mRobotAPI;
    private final DataRepository dataRepository;
    private final CustomAudioManager customAudioManager;
    private final WebSocketHandler webSocketHandler;

    public RobotViewModelFactory(NuwaRobotAPI mRobotAPI,
                                 DataRepository dataRepository,
                                 CustomAudioManager customAudioManager,
                                 WebSocketHandler webSocketHandler) {
        this.mRobotAPI = mRobotAPI;
        this.dataRepository = dataRepository;
        this.customAudioManager = customAudioManager;
        this.webSocketHandler = webSocketHandler;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(RobotViewModel.class)) {
            return (T) new RobotViewModel(mRobotAPI, dataRepository, customAudioManager, webSocketHandler);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}