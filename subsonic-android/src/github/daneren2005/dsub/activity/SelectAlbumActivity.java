/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package github.daneren2005.dsub.activity;

import github.daneren2005.dsub.view.EntryAdapter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import com.actionbarsherlock.view.Menu;
import github.daneren2005.dsub.R;
import github.daneren2005.dsub.domain.MusicDirectory;
import github.daneren2005.dsub.service.*;
import github.daneren2005.dsub.util.*;
import java.io.File;

import java.util.*;

public class SelectAlbumActivity extends SubsonicTabActivity {

    private static final String TAG = SelectAlbumActivity.class.getSimpleName();

    private ListView entryList;
    private View footer;
    private View emptyView;
	private boolean hideButtons = false;
    private Button moreButton;
    private boolean licenseValid;
	private boolean showHeader = true;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_album);

        entryList = (ListView) findViewById(R.id.select_album_entries);

        footer = LayoutInflater.from(this).inflate(R.layout.select_album_footer, entryList, false);
        entryList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        entryList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0) {
                    MusicDirectory.Entry entry = (MusicDirectory.Entry) parent.getItemAtPosition(position);
                    if (entry.isDirectory()) {
                        Intent intent = new Intent(SelectAlbumActivity.this, SelectAlbumActivity.class);
                        intent.putExtra(Constants.INTENT_EXTRA_NAME_ID, entry.getId());
                        intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, entry.getTitle());
                        Util.startActivityWithoutTransition(SelectAlbumActivity.this, intent);
                    } else if (entry.isVideo()) {
                        playExternalPlayer(entry);
                    }
                }
            }
        });
		
        moreButton = (Button) footer.findViewById(R.id.select_album_more);
        emptyView = findViewById(R.id.select_album_empty);

        registerForContextMenu(entryList);

        String id = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ID);
        String name = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_NAME);
        String playlistId = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
        String playlistName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
        String albumListType = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
        int albumListSize = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
        int albumListOffset = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0);

        if (playlistId != null) {
            getPlaylist(playlistId, playlistName);
        } else if (albumListType != null) {
            getAlbumList(albumListType, albumListSize, albumListOffset);
        } else {
            getMusicDirectory(id, name);
        }
    }
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        com.actionbarsherlock.view.MenuInflater inflater = getSupportMenuInflater();
		if(hideButtons) {
			inflater.inflate(R.menu.select_album, menu);
			hideButtons = false;
		} else {
			if(Util.isOffline(this)) {
				inflater.inflate(R.menu.select_song_offline, menu);
			}
			else {
				inflater.inflate(R.menu.select_song, menu);
				
				String playlistId = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
				if(playlistId == null) {
					menu.removeItem(R.id.menu_remove_playlist);
				}
			}
		}
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
		Intent intent;
        switch (item.getItemId()) {
			case R.id.menu_play_now:
				playNow(false, false);
				return true;
			case R.id.menu_play_last:
				playNow(false, true);
				return true;
			case R.id.menu_shuffle:
				playNow(true, false);
				return true;
			case R.id.menu_select:
				selectAllOrNone();
				return true;
			case R.id.menu_refresh:
				refresh();
				return true;
			case R.id.menu_download:
				downloadBackground(false);
                selectAll(false, false);
				return true;
			case R.id.menu_cache:
				downloadBackground(true);
                selectAll(false, false);
				return true;
			case R.id.menu_delete:
				delete();
                selectAll(false, false);
				return true;
			case R.id.menu_add_playlist:
				addToPlaylist(getSelectedSongs());
				return true;
			case R.id.menu_remove_playlist:
				String playlistId = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
				String playlistName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
				removeFromPlaylist(playlistId, playlistName, getSelectedIndexes());
				return true;
            case R.id.menu_exit:
                intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true);
                Util.startActivityWithoutTransition(this, intent);
                return true;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.menu_help:
                startActivity(new Intent(this, HelpActivity.class));
                return true;
        }

        return false;
    }

	private void playNow(final boolean shuffle, final boolean append) {
		if(getSelectedSongs().size() > 0) {
			download(append, false, !append, false, shuffle);
			selectAll(false, false);
		}
		else {
			playAll(shuffle, append);
		}
	}
    private void playAll(final boolean shuffle, final boolean append) {
        boolean hasSubFolders = false;
        for (int i = 0; i < entryList.getCount(); i++) {
            MusicDirectory.Entry entry = (MusicDirectory.Entry) entryList.getItemAtPosition(i);
            if (entry != null && entry.isDirectory()) {
                hasSubFolders = true;
                break;
            }
        }

        String id = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ID);
        if (hasSubFolders && id != null) {
            downloadRecursively(id, false, append, !append, shuffle, false);
        } else {
            selectAll(true, false);
            download(append, false, !append, false, shuffle);
            selectAll(false, false);
        }
    }

    private void refresh() {
        finish();
        Intent intent = getIntent();
        intent.putExtra(Constants.INTENT_EXTRA_NAME_REFRESH, true);
        Util.startActivityWithoutTransition(this, intent);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) menuInfo;

        MusicDirectory.Entry entry = (MusicDirectory.Entry) entryList.getItemAtPosition(info.position);

        if (entry.isDirectory()) {
            MenuInflater inflater = getMenuInflater();
			if(Util.isOffline(this))
				inflater.inflate(R.menu.select_album_context_offline, menu);
			else
				inflater.inflate(R.menu.select_album_context, menu);
        } else if(!entry.isVideo()) {
            MenuInflater inflater = getMenuInflater();
			if(Util.isOffline(this)) {
				inflater.inflate(R.menu.select_song_context_offline, menu);
			}
			else {
				inflater.inflate(R.menu.select_song_context, menu);
				String playlistId = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
				if(playlistId == null) {
					menu.removeItem(R.id.song_menu_remove_playlist);
				}
			}
        } else {
			MenuInflater inflater = getMenuInflater();
			if(Util.isOffline(this))
				inflater.inflate(R.menu.select_video_context_offline, menu);
			else
				inflater.inflate(R.menu.select_video_context, menu);
		}

		if (!Util.isOffline(this)) {
			menu.findItem(entry.isDirectory() ? R.id.album_menu_star : R.id.song_menu_star).setTitle(entry.isStarred() ? R.string.common_unstar : R.string.common_star);
		}
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
        MusicDirectory.Entry entry = (MusicDirectory.Entry) entryList.getItemAtPosition(info.position);
        List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>(10);
        songs.add((MusicDirectory.Entry) entryList.getItemAtPosition(info.position));
        switch (menuItem.getItemId()) {
            case R.id.album_menu_play_now:
                downloadRecursively(entry.getId(), false, false, true, false, false);
                break;
			case R.id.album_menu_play_shuffled:
                downloadRecursively(entry.getId(), false, false, true, true, false);
                break;
            case R.id.album_menu_play_last:
                downloadRecursively(entry.getId(), false, true, false, false, false);
                break;
			case R.id.album_menu_download:
                downloadRecursively(entry.getId(), false, true, false, false, true);
                break;
            case R.id.album_menu_pin:
                downloadRecursively(entry.getId(), true, true, false, false, true);
                break;
			case R.id.album_menu_star:
                toggleStarred(entry);
                break;
            case R.id.song_menu_play_now:
                getDownloadService().download(songs, false, true, true, false);
                break;
            case R.id.song_menu_play_next:
                getDownloadService().download(songs, false, false, true, false);
                break;
            case R.id.song_menu_play_last:
                getDownloadService().download(songs, false, false, false, false);
                break;
			case R.id.song_menu_download:
				getDownloadService().downloadBackground(songs, false);
                break;
            case R.id.song_menu_pin:
				getDownloadService().downloadBackground(songs, true);
                break;
			case R.id.song_menu_delete:
				getDownloadService().delete(songs);
                break;
			case R.id.song_menu_add_playlist:
				addToPlaylist(songs);
                break;
			case R.id.song_menu_star:
                toggleStarred(entry);
                break;
			case R.id.song_menu_webview:
				playWebView(entry);
				break;
			case R.id.song_menu_play_external:
				playExternalPlayer(entry);
				break;
			case R.id.song_menu_info:
				displaySongInfo(entry);
				break;
			/*case R.id.song_menu_stream_external:
				streamExternalPlayer(entry);
				break;*/
			case R.id.song_menu_remove_playlist:
				String playlistId = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_ID);
				String playlistName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
				removeFromPlaylist(playlistId, playlistName, Arrays.<Integer>asList(info.position - 1));
				break;
            default:
                return super.onContextItemSelected(menuItem);
        }
        return true;
    }

    private void getMusicDirectory(final String id, String name) {
        setTitle(name);

        new LoadTask() {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception {
                boolean refresh = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_REFRESH, false);
                return service.getMusicDirectory(id, refresh, SelectAlbumActivity.this, this);
            }
        }.execute();
    }

    private void getPlaylist(final String playlistId, final String playlistName) {
        setTitle(playlistName);

        new LoadTask() {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception {
                return service.getPlaylist(playlistId, playlistName, SelectAlbumActivity.this, this);
            }
        }.execute();
    }

    private void getAlbumList(final String albumListType, final int size, final int offset) {
		showHeader = false;

        if ("newest".equals(albumListType)) {
            setTitle(R.string.main_albums_newest);
        } else if ("random".equals(albumListType)) {
            setTitle(R.string.main_albums_random);
        } else if ("highest".equals(albumListType)) {
            setTitle(R.string.main_albums_highest);
        } else if ("recent".equals(albumListType)) {
            setTitle(R.string.main_albums_recent);
        } else if ("frequent".equals(albumListType)) {
            setTitle(R.string.main_albums_frequent);
        } else if ("starred".equals(albumListType)) {
            setTitle(R.string.main_albums_starred);
        }

        new LoadTask() {
            @Override
            protected MusicDirectory load(MusicService service) throws Exception {
            	MusicDirectory result;
            	if ("starred".equals(albumListType)) {
            		result = service.getStarredList(SelectAlbumActivity.this, this);
            	} else {
            		result = service.getAlbumList(albumListType, size, offset, SelectAlbumActivity.this, this);
            	}
                return result;
            }

            @Override
            protected void done(Pair<MusicDirectory, Boolean> result) {
                if (!result.getFirst().getChildren().isEmpty()) {
					if (!("starred".equals(albumListType))) {
						moreButton.setVisibility(View.VISIBLE);
						entryList.addFooterView(footer);
					}

                    moreButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent(SelectAlbumActivity.this, SelectAlbumActivity.class);
                            String type = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE);
                            int size = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, 0);
                            int offset = getIntent().getIntExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, 0) + size;

                            intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_SIZE, size);
                            intent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_OFFSET, offset);
                            Util.startActivityWithoutTransition(SelectAlbumActivity.this, intent);
                        }
                    });
                }
                super.done(result);
            }
        }.execute();
    }

    private void selectAllOrNone() {
        boolean someUnselected = false;
        int count = entryList.getCount();
        for (int i = 0; i < count; i++) {
            if (!entryList.isItemChecked(i) && entryList.getItemAtPosition(i) instanceof MusicDirectory.Entry) {
                someUnselected = true;
                break;
            }
        }
        selectAll(someUnselected, true);
    }

    private void selectAll(boolean selected, boolean toast) {
        int count = entryList.getCount();
        int selectedCount = 0;
        for (int i = 0; i < count; i++) {
            MusicDirectory.Entry entry = (MusicDirectory.Entry) entryList.getItemAtPosition(i);
            if (entry != null && !entry.isDirectory() && !entry.isVideo()) {
                entryList.setItemChecked(i, selected);
                selectedCount++;
            }
        }

        // Display toast: N tracks selected / N tracks unselected
        if (toast) {
            int toastResId = selected ? R.string.select_album_n_selected
                                      : R.string.select_album_n_unselected;
            Util.toast(this, getString(toastResId, selectedCount));
        }
    }

    private List<MusicDirectory.Entry> getSelectedSongs() {
        List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>(10);
        int count = entryList.getCount();
        for (int i = 0; i < count; i++) {
            if (entryList.isItemChecked(i)) {
                songs.add((MusicDirectory.Entry) entryList.getItemAtPosition(i));
            }
        }
        return songs;
    }
	
	private List<Integer> getSelectedIndexes() {
		List<Integer> indexes = new ArrayList<Integer>();
		
		int count = entryList.getCount();
        for (int i = 0; i < count; i++) {
            if (entryList.isItemChecked(i)) {
                indexes.add(i - 1);
            }
        }
		
		return indexes;
	}

    private void download(final boolean append, final boolean save, final boolean autoplay, final boolean playNext, final boolean shuffle) {
        if (getDownloadService() == null) {
            return;
        }

        final List<MusicDirectory.Entry> songs = getSelectedSongs();
        Runnable onValid = new Runnable() {
            @Override
            public void run() {
                if (!append) {
                    getDownloadService().clear();
                }

                warnIfNetworkOrStorageUnavailable();
                getDownloadService().download(songs, save, autoplay, playNext, shuffle);
                String playlistName = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PLAYLIST_NAME);
                if (playlistName != null) {
                    getDownloadService().setSuggestedPlaylistName(playlistName);
                }
                if (autoplay) {
                    Util.startActivityWithoutTransition(SelectAlbumActivity.this, DownloadActivity.class);
                } else if (save) {
                    Util.toast(SelectAlbumActivity.this,
                               getResources().getQuantityString(R.plurals.select_album_n_songs_downloading, songs.size(), songs.size()));
                } else if (append) {
                    Util.toast(SelectAlbumActivity.this,
                               getResources().getQuantityString(R.plurals.select_album_n_songs_added, songs.size(), songs.size()));
                }
            }
        };

        checkLicenseAndTrialPeriod(onValid);
    }
	private void downloadBackground(final boolean save) {
		List<MusicDirectory.Entry> songs = getSelectedSongs();
		if(songs.isEmpty()) {
			selectAll(true, false);
			songs = getSelectedSongs();
		}
		downloadBackground(save, songs);
	}
	private void downloadBackground(final boolean save, final List<MusicDirectory.Entry> songs) {
		if (getDownloadService() == null) {
			return;
		}

		Runnable onValid = new Runnable() {
			@Override
			public void run() {
				warnIfNetworkOrStorageUnavailable();
				getDownloadService().downloadBackground(songs, save);

				Util.toast(SelectAlbumActivity.this,
					getResources().getQuantityString(R.plurals.select_album_n_songs_downloading, songs.size(), songs.size()));
			}
		};

		checkLicenseAndTrialPeriod(onValid);
	}

    private void delete() {
		List<MusicDirectory.Entry> songs = getSelectedSongs();
		if(songs.isEmpty()) {
			selectAll(true, false);
			songs = getSelectedSongs();
		}
        if (getDownloadService() != null) {
            getDownloadService().delete(songs);
        }
    }

    private void playWebView(MusicDirectory.Entry entry) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(MusicServiceFactory.getMusicService(this).getVideoUrl(this, entry.getId())));
		
        startActivity(intent);
    }
	private void playExternalPlayer(MusicDirectory.Entry entry) {
		DownloadFile check = new DownloadFile(this, entry, false);
		if(!check.isCompleteFileAvailable()) {
			Util.toast(this, R.string.download_need_download);
		} else {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.parse(entry.getPath()), "video/*");
			startActivity(intent);
		}
	}
	private void streamExternalPlayer(MusicDirectory.Entry entry) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(MusicServiceFactory.getMusicService(this).getVideoStreamUrl(this, entry.getId())), "video/*");
		
		List<ResolveInfo> intents = getPackageManager()
			.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		if(intents != null && intents.size() > 0) {
			startActivity(intent);
		} else {
			Util.toast(this, R.string.download_no_streaming_player);
		}
	}

    private void checkLicenseAndTrialPeriod(Runnable onValid) {
        if (licenseValid) {
            onValid.run();
            return;
        }

        int trialDaysLeft = Util.getRemainingTrialDays(this);
        Log.i(TAG, trialDaysLeft + " trial days left.");

        if (trialDaysLeft == 0) {
            showDonationDialog(trialDaysLeft, null);
        } else if (trialDaysLeft < Constants.FREE_TRIAL_DAYS / 2) {
            showDonationDialog(trialDaysLeft, onValid);
        } else {
            Util.toast(this, getResources().getString(R.string.select_album_not_licensed, trialDaysLeft));
            onValid.run();
        }
    }

    private void showDonationDialog(int trialDaysLeft, final Runnable onValid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_info);

        if (trialDaysLeft == 0) {
            builder.setTitle(R.string.select_album_donate_dialog_0_trial_days_left);
        } else {
            builder.setTitle(getResources().getQuantityString(R.plurals.select_album_donate_dialog_n_trial_days_left,
                                                              trialDaysLeft, trialDaysLeft));
        }

        builder.setMessage(R.string.select_album_donate_dialog_message);

        builder.setPositiveButton(R.string.select_album_donate_dialog_now,
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DONATION_URL)));
				}
			});

        builder.setNegativeButton(R.string.select_album_donate_dialog_later,
			new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialogInterface, int i) {
					dialogInterface.dismiss();
					if (onValid != null) {
						onValid.run();
					}
				}
			});

        builder.create().show();
    }

    private abstract class LoadTask extends TabActivityBackgroundTask<Pair<MusicDirectory, Boolean>> {

        public LoadTask() {
            super(SelectAlbumActivity.this);
        }

        protected abstract MusicDirectory load(MusicService service) throws Exception;

        @Override
        protected Pair<MusicDirectory, Boolean> doInBackground() throws Throwable {
            MusicService musicService = MusicServiceFactory.getMusicService(SelectAlbumActivity.this);
            MusicDirectory dir = load(musicService);
            boolean valid = musicService.isLicenseValid(SelectAlbumActivity.this, this);
            return new Pair<MusicDirectory, Boolean>(dir, valid);
        }

        @Override
        protected void done(Pair<MusicDirectory, Boolean> result) {
            List<MusicDirectory.Entry> entries = result.getFirst().getChildren();

            int songCount = 0;
            for (MusicDirectory.Entry entry : entries) {
                if (!entry.isDirectory()) {
                    songCount++;
                }
            }

            if (songCount > 0) {
                getImageLoader().loadImage(getSupportActionBar(), entries.get(0));
				if(showHeader) {
					entryList.addHeaderView(createHeader(entries), null, false);
				}
            } else {
				hideButtons = true;
				invalidateOptionsMenu();
			}

            emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            entryList.setAdapter(new EntryAdapter(SelectAlbumActivity.this, getImageLoader(), entries, true));
            licenseValid = result.getSecond();

            boolean playAll = getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_AUTOPLAY, false);
            if (playAll && songCount > 0) {
                playAll(getIntent().getBooleanExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, false), false);
            }
        }
    }
	
	private View createHeader(List<MusicDirectory.Entry> entries) {
        View header = LayoutInflater.from(this).inflate(R.layout.select_album_header, entryList, false);

        View coverArtView = header.findViewById(R.id.select_album_art);
        getImageLoader().loadImage(coverArtView, entries.get(0), true, true);

        TextView titleView = (TextView) header.findViewById(R.id.select_album_title);
        titleView.setText(getTitle());

        int songCount = 0;

        Set<String> artists = new HashSet<String>();
        for (MusicDirectory.Entry entry : entries) {
            if (!entry.isDirectory()) {
                songCount++;
                if (entry.getArtist() != null) {
                    artists.add(entry.getArtist());
                }
            }
        }

        TextView artistView = (TextView) header.findViewById(R.id.select_album_artist);
        if (artists.size() == 1) {
            artistView.setText(artists.iterator().next());
            artistView.setVisibility(View.VISIBLE);
        } else {
            artistView.setVisibility(View.GONE);
        }

        TextView songCountView = (TextView) header.findViewById(R.id.select_album_song_count);
        String s = getResources().getQuantityString(R.plurals.select_album_n_songs, songCount, songCount);
        songCountView.setText(s.toUpperCase());

        return header;
    }
	
	public void removeFromPlaylist(final String id, final String name, final List<Integer> indexes) {
		new LoadingTask<Void>(this, true) {
            @Override
            protected Void doInBackground() throws Throwable {				
                MusicService musicService = MusicServiceFactory.getMusicService(SelectAlbumActivity.this);
				musicService.removeFromPlaylist(id, indexes, SelectAlbumActivity.this, null);
                return null;
            }
            
            @Override
            protected void done(Void result) {
				refresh();
                Util.toast(SelectAlbumActivity.this, getResources().getString(R.string.removed_playlist, indexes.size(), name));
            }
            
            @Override
            protected void error(Throwable error) {            	
            	String msg;
            	if (error instanceof OfflineException || error instanceof ServerTooOldException) {
            		msg = getErrorMessage(error);
            	} else {
            		msg = getResources().getString(R.string.updated_playlist_error, name) + " " + getErrorMessage(error);
            	}
            	
        		Util.toast(SelectAlbumActivity.this, msg, false);
            }
        }.execute();
	}
}
