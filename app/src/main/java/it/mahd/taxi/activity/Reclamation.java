package it.mahd.taxi.activity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import it.mahd.taxi.R;

/**
 * Created by salem on 2/13/16.
 */
public class Reclamation extends Fragment {
    public Reclamation() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.reclamation, container, false);
        return rootView;
    }
}
