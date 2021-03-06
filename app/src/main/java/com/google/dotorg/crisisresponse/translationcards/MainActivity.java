/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.dotorg.crisisresponse.translationcards;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Activity for the main screen, with lists of phrases to play.
 *
 * @author nick.c.worden@gmail.com (Nick Worden)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final String FEEDBACK_URL =
            "https://docs.google.com/forms/d/1p8nJlpFSv03MXWf67pjh_fHyOfjbK9LJgF8hORNcvNM/" +
                    "viewform?entry.1158658650=0.2.0";

    private static final int REQUEST_KEY_ADD_CARD = 1;
    private static final int REQUEST_KEY_EDIT_CARD = 2;

    private DbManager dbm;
    private Dictionary[] dictionaries;
    private int currentDictionaryIndex;
    private MediaPlayerManager lastMediaPlayerManager;
    private int lastPlayedPosition;
    private TextView[] languageTabTextViews;
    private View[] languageTabBorders;
    private ArrayAdapter<String> listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbm = new DbManager(this);
        dictionaries = dbm.getAllDictionaries();
        currentDictionaryIndex = -1;
        lastPlayedPosition = -1;
        setContentView(R.layout.activity_main);
        initTabs();
        initList();
        initFeedbackButton();
        setDictionary(0);
    }

    private void initTabs() {
        LayoutInflater inflater = LayoutInflater.from(this);
        languageTabTextViews = new TextView[dictionaries.length];
        languageTabBorders = new View[dictionaries.length];
        LinearLayout tabContainer = (LinearLayout) findViewById(R.id.tabs);
        for (int i = 0; i < dictionaries.length; i++) {
            Dictionary dictionary = dictionaries[i];
            View textFrame = inflater.inflate(R.layout.language_tab, tabContainer, false);
            TextView textView = (TextView) textFrame.findViewById(R.id.tab_label_text);
            textView.setText(dictionary.getLabel().toUpperCase());
            final int index = i;
            textFrame.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (lastMediaPlayerManager != null) {
                        lastMediaPlayerManager.stop();
                        lastPlayedPosition = -1;
                    }
                    setDictionary(index);
                }
            });
            tabContainer.addView(textFrame);
            languageTabTextViews[i] = textView;
            languageTabBorders[i] = textFrame.findViewById(R.id.tab_border);
        }
    }

    private void initList() {
        ListView list = (ListView) findViewById(R.id.list);
        LayoutInflater layoutInflater = getLayoutInflater();
        list.addHeaderView(layoutInflater.inflate(R.layout.main_list_header, list, false));
        list.addFooterView(layoutInflater.inflate(R.layout.main_list_footer, list, false));
        listAdapter = new CardListAdapter(
                this, R.layout.list_item, R.id.card_text, new ArrayList<String>(), list);
        list.setAdapter(listAdapter);
        ImageButton addTranslationButton = (ImageButton) findViewById(R.id.add_button);
        addTranslationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddTranslationDialog();
            }
        });
    }

    private void initFeedbackButton() {
        findViewById(R.id.main_feedback_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_URL)));
            }
        });
    }

    private void setDictionary(int dictionaryIndex) {
        if (currentDictionaryIndex != -1) {
            languageTabTextViews[currentDictionaryIndex].setTextColor(
                    getResources().getColor(R.color.unselectedLanguageTabText));
            languageTabBorders[currentDictionaryIndex].setBackgroundColor(0);
        }
        languageTabTextViews[dictionaryIndex].setTextColor(
                getResources().getColor(R.color.textColor));
        languageTabBorders[dictionaryIndex].setBackgroundColor(
                getResources().getColor(R.color.textColor));
        currentDictionaryIndex = dictionaryIndex;
        Dictionary dictionary = dictionaries[dictionaryIndex];
        listAdapter.clear();
        for (int translationIndex = 0;
             translationIndex < dictionary.getTranslationCount();
             translationIndex++) {
            listAdapter.add(dictionary.getTranslation(translationIndex).getLabel());
        }

    }

    private void play(int translationIndex, final ProgressBar progressBar) {
        if (lastMediaPlayerManager != null) {
            lastMediaPlayerManager.stop();
        }
        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            dictionaries[currentDictionaryIndex]
                    .getTranslation(translationIndex)
                    .setMediaPlayerDataSource(this, mediaPlayer);
            mediaPlayer.prepare();
        } catch (IOException e) {
            Log.d(TAG, "Error getting audio asset: " + e);
            return;
        }
        lastMediaPlayerManager = new MediaPlayerManager(mediaPlayer, progressBar);
        mediaPlayer.setOnCompletionListener(
                new ManagedMediaPlayerCompletionListener(lastMediaPlayerManager));
        progressBar.setMax(mediaPlayer.getDuration());
        mediaPlayer.start();
        new Thread(lastMediaPlayerManager).start();
    }

    private void showAddTranslationDialog() {
        Intent intent = new Intent(this, RecordingActivity.class);
        intent.putExtra(
                RecordingActivity.INTENT_KEY_DICTIONARY_ID,
                dictionaries[currentDictionaryIndex].getDbId());
        intent.putExtra(
                RecordingActivity.INTENT_KEY_DICTIONARY_LABEL,
                dictionaries[currentDictionaryIndex].getLabel());
        startActivityForResult(intent, REQUEST_KEY_ADD_CARD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_KEY_ADD_CARD:
            case REQUEST_KEY_EDIT_CARD:
                if (resultCode == RESULT_OK) {
                    dictionaries = dbm.getAllDictionaries();
                    setDictionary(currentDictionaryIndex);
                }
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private class CardListAdapter extends ArrayAdapter<String> {

        private final View.OnClickListener clickListener;
        private final View.OnClickListener editClickListener;

        public CardListAdapter(
                Context context, int resource, int textViewResourceId, List<String> objects,
                ListView list) {
            super(context, resource, textViewResourceId, objects);
            clickListener = new CardClickListener(list);
            editClickListener = new CardEditClickListener(list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = null;
            if (convertView == null) {
                LayoutInflater layoutInflater = getLayoutInflater();
                view = layoutInflater.inflate(R.layout.list_item, parent, false);
                view.findViewById(R.id.card_text).setOnClickListener(clickListener);
                view.findViewById(R.id.card_edit).setOnClickListener(editClickListener);
            } else {
                view = convertView;
            }
            TextView cardTextView = (TextView) view.findViewById(R.id.card_text);
            cardTextView.setText(getItem(position));
            return view;
        }
    }

    private class CardClickListener implements View.OnClickListener {

        private final ListView list;

        public CardClickListener(ListView list) {
            this.list = list;
        }

        @Override
        public void onClick(View view) {
            View listItem = (View) view.getParent().getParent().getParent();
            int position = list.getPositionForView(listItem);
            if (position == 0 ||
                    position > dictionaries[currentDictionaryIndex].getTranslationCount()) {
                // It's a click on the header or footer bumper, ignore it.
                return;
            }
            int itemIndex = position - 1;
            if (lastPlayedPosition == itemIndex && lastMediaPlayerManager.stop()) {
                return;
            }
            lastPlayedPosition = itemIndex;
            play(itemIndex, (ProgressBar) listItem.findViewById(R.id.list_item_progress_bar));
        }
    }

    private class CardEditClickListener implements View.OnClickListener {

        private final ListView list;

        public CardEditClickListener(ListView list) {
            this.list = list;
        }

        @Override
        public void onClick(View view) {
            View listItem = (View) view.getParent().getParent().getParent();
            int position = list.getPositionForView(listItem);
            if (position == 0 ||
                    position > dictionaries[currentDictionaryIndex].getTranslationCount()) {
                // It's a click on the header or footer bumper, ignore it.
                return;
            }
            int itemIndex = position - 1;
            Intent intent = new Intent(MainActivity.this, RecordingActivity.class);
            Dictionary dictionary = dictionaries[currentDictionaryIndex];
            Dictionary.Translation translation = dictionary.getTranslation(itemIndex);
            intent.putExtra(RecordingActivity.INTENT_KEY_DICTIONARY_ID, dictionary.getDbId());
            intent.putExtra(RecordingActivity.INTENT_KEY_DICTIONARY_LABEL, dictionary.getLabel());
            intent.putExtra(RecordingActivity.INTENT_KEY_TRANSLATION_ID, translation.getDbId());
            intent.putExtra(RecordingActivity.INTENT_KEY_TRANSLATION_LABEL, translation.getLabel());
            intent.putExtra(
                    RecordingActivity.INTENT_KEY_TRANSLATION_IS_ASSET, translation.getIsAsset());
            intent.putExtra(
                    RecordingActivity.INTENT_KEY_TRANSLATION_FILENAME, translation.getFilename());
            startActivityForResult(intent, REQUEST_KEY_EDIT_CARD);
        }
    }

    private class MediaPlayerManager implements Runnable {

        private final Lock lock;
        private boolean running;
        private final MediaPlayer mediaPlayer;
        private final ProgressBar progressBar;

        public MediaPlayerManager(MediaPlayer mediaPlayer, ProgressBar progressBar) {
            lock = new ReentrantLock();
            running = true;
            this.mediaPlayer = mediaPlayer;
            this.progressBar = progressBar;
        }

        public boolean stop() {
            lock.lock();
            if (!running) {
                // Already stopped, just return false.
                lock.unlock();
                return false;
            }
            running = false;
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.release();
            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setProgress(0);
                }
            });
            lock.unlock();
            return true;
        }

        private boolean tryUpdate() {
            lock.lock();
            if (!running) {
                lock.unlock();
                return false;
            }
            final int currentPosition = mediaPlayer.getCurrentPosition();
            progressBar.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setProgress(currentPosition);
                }
            });
            lock.unlock();
            return true;
        }

        @Override
        public void run() {
            while (tryUpdate()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Do nothing.
                }
            }
        }
    }

    private class ManagedMediaPlayerCompletionListener implements MediaPlayer.OnCompletionListener {

        private MediaPlayerManager manager;

        public ManagedMediaPlayerCompletionListener(MediaPlayerManager manager) {
            super();
            this.manager = manager;
        }

        @Override
        public void onCompletion(MediaPlayer mp) {
            manager.stop();
        }
    }
}
