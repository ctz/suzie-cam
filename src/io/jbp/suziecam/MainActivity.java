package io.jbp.suziecam;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONObject;

import android.app.ActionBar;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Bundle;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends FragmentActivity implements ActionBar.OnNavigationListener
{
  /**
   * The serialization (saved instance state) Bundle key representing the
   * current dropdown position.
   */
  private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";

  public static int SECTIONS[] = new int[]
  {
   R.string.title_minus0,
   R.string.title_minus1,
   R.string.title_minus5,
   R.string.title_minus15,
   R.string.title_minus30,
   R.string.title_minus60,
   R.string.title_minus120,
  };

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // Set up the action bar to show a dropdown list.
    final ActionBar actionBar = getActionBar();
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

    String sectionTitles[] = new String[SECTIONS.length];
    for (int i = 0; i < SECTIONS.length; i++)
      sectionTitles[i] = getString(SECTIONS[i]);

    // Set up the dropdown list navigation in the action bar.
    actionBar.setListNavigationCallbacks(
                                         // Specify a SpinnerAdapter to populate
                                         // the dropdown list.
                                         new ArrayAdapter<String>(
                                                                  actionBar.getThemedContext(),
                                                                  android.R.layout.simple_list_item_1,
                                                                  android.R.id.text1,
                                                                  sectionTitles),
                                         this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu m)
  {
    MenuInflater inf = getMenuInflater();
    inf.inflate(R.menu.main, m);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem m)
  {
    switch (m.getItemId())
    {
    case R.id.refresh_menu:
      ImageSectionFragment sec = (ImageSectionFragment) getSupportFragmentManager().findFragmentByTag("image");
      if (sec != null)
        sec.refresh();
      return true;
    }

    return false;
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState)
  {
    // Restore the previously serialized current dropdown position.
    if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM))
    {
      getActionBar().setSelectedNavigationItem(
                                               savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM));
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState)
  {
    // Serialize the current dropdown position.
    outState.putInt(STATE_SELECTED_NAVIGATION_ITEM,
                    getActionBar().getSelectedNavigationIndex());
  }

  @Override
  public boolean onNavigationItemSelected(int position, long id)
  {
    // When the given dropdown item is selected, show its contents in the
    // container view.
    Fragment fragment = new ImageSectionFragment();
    Bundle args = new Bundle();
    args.putInt(ImageSectionFragment.ARG_SECTION_NUMBER, position);
    fragment.setArguments(args);
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.container, fragment, "image")
        .commit();
    return true;
  }

  public static class ImageSectionFragment extends Fragment
  {
    private static final int AUTO_REFRESH_TIME_MS = 20000;

    /**
     * The fragment argument representing the section number for this fragment.
     */
    public static final String ARG_SECTION_NUMBER = "section_number";

    private ImageView image;
    private TextView updateText;
    private ProgressBar metaSpinner;
    private ProgressBar imageSpinner;
    private Date currentImageTime;

    public ImageSectionFragment()
    {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
      View rootView = inflater.inflate(R.layout.fragment_main, container, false);
      image = (ImageView) rootView.findViewById(R.id.image);
      updateText = (TextView) rootView.findViewById(R.id.updateText);
      metaSpinner = (ProgressBar) rootView.findViewById(R.id.refreshProgress);
      imageSpinner = (ProgressBar) rootView.findViewById(R.id.imageLoadProgress);

      updateText.setText(R.string.loading_images);
      metaSpinner.setVisibility(View.VISIBLE);
      imageSpinner.setVisibility(View.VISIBLE);
      image.setVisibility(View.INVISIBLE);
      return rootView;
    }

    @Override
    public void onResume()
    {
      super.onResume();
      loadMetaNow.run();
    }
    
    public void refresh()
    {
      loadMetaNow.run();
    }

    @Override
    public void onStop()
    {
      super.onStop();
      updateText = null;
      metaSpinner = null;
      imageSpinner = null;
    }

    Runnable loadMetaNow = new Runnable()
    {
      @Override
      public void run()
      {
        int section = SECTIONS[getArguments().getInt(ARG_SECTION_NUMBER)];
        startMetaLoad(section);
      }
    };

    Runnable updateTime = new Runnable()
    {
      @Override
      public void run()
      {
        if (currentImageTime == null || updateText == null)
          return;

        long diffSecs = (System.currentTimeMillis() - currentImageTime.getTime()) / 1000;
        if (diffSecs < 0)
          updateText.setText(String.format("%d seconds in the future (clock skew)", diffSecs));
        else if (diffSecs < 60)
          updateText.setText(String.format("%d seconds ago", diffSecs));
        else if (diffSecs < 3600)
          updateText.setText(String.format("%d minutes ago", diffSecs / 60));
        else
          updateText.setText(String.format("%d hours ago", diffSecs / 3600));

        updateText.postDelayed(this, 1000);
      }
    };

    private void scheduleRetry()
    {
      updateText.postDelayed(loadMetaNow, AUTO_REFRESH_TIME_MS);
    }

    private void startMetaLoad(final int section)
    {
      if (metaSpinner == null)
        return;

      currentImageTime = null;
      metaSpinner.setVisibility(View.VISIBLE);

      InfoCache.startRead(getActivity(), section, new InfoCache.CompleteCallback()
      {
        @Override
        public void onComplete(JSONObject info)
        {
          onMeta(info);
          scheduleRetry();
        }

        @Override
        public void onFailure(Exception e)
        {
          if (metaSpinner == null)
            return;
          updateText.setText("Failed: " + e.getMessage());
          metaSpinner.setVisibility(View.INVISIBLE);
          scheduleRetry();
        }
      });
    }

    private void onMeta(JSONObject info)
    {
      if (metaSpinner == null)
        return;

      metaSpinner.setVisibility(View.INVISIBLE);

      if (info.opt("server_time") == null ||
          info.opt("filename") == null)
      {
        currentImageTime = null;
        updateText.setText("No image for that far back");
        return;
      }

      SimpleDateFormat fmt = new SimpleDateFormat(Config.ISO8601DATE_FORMAT);
      try
      {
        currentImageTime = fmt.parse(info.optString("server_time"));
      } catch (ParseException e)
      {
        currentImageTime = null;
      }

      updateTime.run();
      
      if (!ImageCache.haveCache(getActivity(), info.optString("filename")))
         imageSpinner.setVisibility(View.VISIBLE);
      
      ImageCache.startRead(getActivity(), info, new ImageCache.CompleteCallback()
      {
        @Override
        public void onComplete(Drawable d)
        {
          image.setImageDrawable(d);
          image.setVisibility(View.VISIBLE);
          imageSpinner.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onFailure(Exception e)
        {
          currentImageTime = null;

          image.setVisibility(View.INVISIBLE);

          imageSpinner.setVisibility(View.VISIBLE);

          updateText.setText("Failed: " + e.getMessage());
          metaSpinner.setVisibility(View.INVISIBLE);
        }
      });
    }
  }

}
