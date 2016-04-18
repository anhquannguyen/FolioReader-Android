/*
* Copyright (C) 2016 Pedro Paulo de Amorim
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.folioreader.activity;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.folioreader.R;
import com.folioreader.adapter.FolioPageFragmentAdapter;
import com.folioreader.adapter.TOCAdapter;
import com.folioreader.fragments.FolioPageFragment;
import com.folioreader.view.ConfigView;
import com.folioreader.view.ConfigViewCallback;
import com.folioreader.view.FolioView;
import com.folioreader.view.FolioViewCallback;
import com.folioreader.view.VerticalViewPager;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubReader;

public class FolioActivity extends AppCompatActivity implements ConfigViewCallback,
        FolioViewCallback, FolioPageFragment.FolioPageFragmentCallback {

    public static final String INTENT_EPUB_ASSET_PATH = "com.folioreader.epub_asset_path";
    private RecyclerView recyclerViewMenu;
    private VerticalViewPager mFolioPageViewPager;
    private FolioView folioView;
    private ConfigView configView;

    private String mEpubAssetPath;
    private Book mBook;
    private ArrayList<TOCReference> mTocReferences = new ArrayList<>();
    private List<SpineReference> mSpineReferences;
    private List<String> mSpineReferenceHtmls = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.folio_activity);
        mEpubAssetPath = getIntent().getStringExtra(INTENT_EPUB_ASSET_PATH);
        loadBook();

    }

    private void loadBook() {
        AssetManager assetManager = getAssets();
        try {
            InputStream epubInputStream = assetManager.open(mEpubAssetPath);
            mBook = (new EpubReader()).readEpub(epubInputStream);
            populateTableOfContents(mBook.getTableOfContents().getTocReferences(), 0);
            Spine spine = new Spine(mBook.getTableOfContents());
            this.mSpineReferences = spine.getSpineReferences();
            for (int i=0; i<mSpineReferences.size(); i++) mSpineReferenceHtmls.add(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void populateTableOfContents(List<TOCReference> tocReferences, int depth) {
        if (tocReferences == null) {
            return;
        }

        for (TOCReference tocReference : tocReferences) {
            mTocReferences.add(tocReference);
            populateTableOfContents(tocReference.getChildren(), depth + 1);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        recyclerViewMenu = (RecyclerView) findViewById(R.id.recycler_view_menu);
        folioView = (FolioView) findViewById(R.id.folio_view);
        configView = (ConfigView) findViewById(R.id.config_view);
        configRecyclerViews();
        configFolio();
        configDrawerLayoutButtons();
    }

    @Override
    public void onBackPressed() {
        if (configView.isDragViewAboveTheLimit()) {
            configView.moveToOriginalPosition();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onBackgroundUpdate(int value) {
        recyclerViewMenu.setBackgroundColor(value);
        folioView.setBackgroundColor(value);
    }

    @Override
    public void onShadowAlpha(float alpha) {
        folioView.updateShadowAlpha(alpha);
    }

    @Override
    public void showShadow() {
        folioView.resetView();
    }

    @Override
    public void onConfigChange() {

        int position = mFolioPageViewPager.getCurrentItem();
        //reload previous, current and next fragment
        Fragment page;
        if (position != 0) {
            page = getFragment(position - 1);
            ((FolioPageFragment) page).reload();
        }
        page = getFragment(position);
        ((FolioPageFragment) page).reload();
        if (position < mSpineReferences.size()) {
            page = getFragment(position + 1);
            ((FolioPageFragment) page).reload();
        }
    }

    private Fragment getFragment(int pos){
        return getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.folioPageViewPager + ":" +(pos));
    }

    @Override
    public void onShadowClick() {
        configView.moveOffScreen();
    }

    private void configRecyclerViews() {
        recyclerViewMenu.setLayoutManager(new LinearLayoutManager(this));
        if (mTocReferences != null) {
            TOCAdapter tocAdapter = new TOCAdapter(mTocReferences);
            recyclerViewMenu.setAdapter(tocAdapter);
        }
    }

    private void configFolio() {
        mFolioPageViewPager = (VerticalViewPager) folioView.findViewById(R.id.folioPageViewPager);
        if (mSpineReferences != null) {
            FolioPageFragmentAdapter folioPageFragmentAdapter = new FolioPageFragmentAdapter(getSupportFragmentManager(), mSpineReferences);
            mFolioPageViewPager.setAdapter(folioPageFragmentAdapter);
        }

        folioView.setFolioViewCallback(this);
        configView.setConfigViewCallback(this);
    }

    private void configDrawerLayoutButtons(){
        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((DrawerLayout)findViewById(R.id.drawer_left)).closeDrawers();
                finish();
            }
        });
        findViewById(R.id.btn_config).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((DrawerLayout)findViewById(R.id.drawer_left)).closeDrawers();
                if (configView.isDragViewAboveTheLimit()) {
                    configView.moveToOriginalPosition();
                } else {
                    configView.moveOffScreen();
                }
            }
        });
    }

    @Override
    public String getChapterHtmlContent(int position) {
        return reader(position);
    }

    private String reader(int position) {
        if (mSpineReferenceHtmls.get(position)!=null){
            return mSpineReferenceHtmls.get(position);
        } else {
            try {
                Reader reader = mSpineReferences.get(position).getResource().getReader();

                StringBuilder builder = new StringBuilder();
                int numChars;
                char[] cbuf = new char[2048];
                while ((numChars = reader.read(cbuf)) >= 0) {
                    builder.append(cbuf, 0, numChars);
                }
                String content = builder.toString();
                mSpineReferenceHtmls.set(position, content);
                return content;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "";
        }
    }
}
