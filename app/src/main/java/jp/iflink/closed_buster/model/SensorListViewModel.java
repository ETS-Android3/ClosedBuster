package jp.iflink.closed_buster.model;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SensorListViewModel extends ViewModel {
    // ユーザの入力情報
    private MutableLiveData<SensorListItem> userInput;

    public MutableLiveData<SensorListItem> getUserInput(){
        if (userInput == null) {
            userInput = new MutableLiveData<SensorListItem>();
        }
        return userInput;
    }

}
