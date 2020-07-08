package tv.paddi.android.adapters;

import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaStream;
import tv.paddi.android.R;
import tv.paddi.android.utils.NetworkConnection;

public class RemoteAdapter extends ArrayAdapter<RemoteAdapter.RemoteView> {

	private static final String TAG = RemoteAdapter.class.getSimpleName();
	private final LayoutInflater inflater;
	private NetworkConnection networkConnection;
	private FirebaseFirestore firebaseFirestore;
	private CollectionReference mainRef;

	static class RemoteView {
		String peerId;
		MediaStream stream;
		Canvas canvas;
		View viewHolder;
	}

	public RemoteAdapter(final Context context) {
		super(context, 0);
		this.inflater = LayoutInflater.from(context);
		firebaseFirestore = FirebaseFirestore.getInstance();
        mainRef = firebaseFirestore.collection("Paddi").document("Files").collection("Users");
	}

	@Override
	public @NonNull
	View getView(int position, View convertView, @NonNull ViewGroup parent) {
		View view;
		networkConnection = new NetworkConnection();
		Log.d(TAG, "getView(" + position + ")");

		RemoteView item = getItem(position);
		if (null != item) {
			if (null == item.viewHolder) {
				item.viewHolder = inflater.inflate(R.layout.view_remote, parent, false);
				ImageView flagRemotePeerId = item.viewHolder.findViewById(R.id.flag);

				//============= Flag ================
				flagRemotePeerId.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						PopupMenu popup = new PopupMenu(getContext(), flagRemotePeerId);
						MenuInflater inflater = popup.getMenuInflater();
						inflater.inflate(R.menu.report_menu, popup.getMenu());
						popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
							@Override
							public boolean onMenuItemClick(MenuItem menuItem) {
								if (menuItem.getItemId() == R.id.report_action) {
									AlertDialog.Builder update_builder = new AlertDialog.Builder(getContext());
									update_builder
											.setMessage(getContext().getResources().getString(R.string.report_user))
											.setPositiveButton(getContext().getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
												@Override
												public void onClick(DialogInterface dialog, int which) {
													if (!networkConnection.isConnected(getContext())) {
														Toast.makeText(getContext(),
																getContext().getResources().getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
													} else {
														setReportFlag(item.peerId);
													}
												}
											}).setNegativeButton(getContext().getResources().getString(R.string.no), null);

									AlertDialog alert_update = update_builder.create();
									alert_update.show();
									return true;
								}
								return false;
							}
						});
						popup.show();
					}
				});

				item.canvas = item.viewHolder.findViewById(R.id.cvsRemote);
				item.stream.addVideoRenderer(item.canvas, 0);
				view = item.viewHolder;
			} else {
				view = item.viewHolder;
				item.canvas.requestLayout();
			}
		}
		else if (null == convertView) {
			view = inflater.inflate(R.layout.view_unknown_remote, parent, false);
		}
		else {
			view = convertView;
		}

		return view;
	}

	private void setReportFlag(String userId){
		try {
			mainRef.document(userId).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
				@Override
				public void onComplete(@NonNull Task<DocumentSnapshot> task) {
					if (task.isSuccessful()) {
						if (task.getResult().exists()) {
							Object flag = task.getResult().get("flag");
							if (flag instanceof String) {
								int count = Integer.parseInt(String.valueOf(flag));
								if (count < 1){
									subFlagMethod(true, userId, false, count);
								}else {
									subFlagMethod(true, userId, true, count);
								}

							} else {
								Toast.makeText(getContext(),
										getContext().getResources().getString(R.string.flag_sent),
										Toast.LENGTH_SHORT).show();
							}
						} else {
							subFlagMethod(false, userId, false, 0);
						}
					} else {
						Toast.makeText(getContext(),
								getContext().getResources().getString(R.string.error), Toast.LENGTH_LONG).show();
					}
				}
			});
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	private void subFlagMethod(boolean exist, String userId, boolean max, int count){
		if (!exist){
			Map<String, Object> flagMap = new HashMap<>();
			flagMap.put("flag", (count + 1));
			mainRef.document(userId).set(flagMap);
		}else {
			if (max) {
				Date utc = new Date(System.currentTimeMillis());
				Map<String, Object> flagMap = new HashMap<>();
				flagMap.put("flag", utc);
				mainRef.document(userId).update(flagMap);
			} else {
				Map<String, Object> flagMap = new HashMap<>();
				flagMap.put("flag", (count + 1));
				mainRef.document(userId).update(flagMap);
			}
		}
	}

	public void add(MediaStream stream) {
		RemoteView item = new RemoteView();
		item.peerId = stream.getPeerId();
		item.stream = stream;
		add(item);
	}

	public void remove(String peerId) {
		RemoteView target = null;

		int count = getCount();
		for (int i = 0; i < count; ++i) {
			RemoteView item = getItem(i);
			if (null != item && item.peerId.equals(peerId)) {
				target = item;
				break;
			}
		}

		if (null != target) {
			removeRenderer(target);
			remove(target);
		}
	}

	public void remove(MediaStream stream) {
		RemoteView target = null;

		int count = getCount();

		for (int i = 0; i < count; ++i) {
			RemoteView item = getItem(i);
			if (null != item && item.stream == stream) {
				target = item;
				break;
			}
		}

		if (null != target) {
			removeRenderer(target);
			remove(target);
		}
	}

	private void removeRenderer(RemoteView item) {
		if (null == item) return;

		if (null != item.canvas) {
			item.stream.removeVideoRenderer(item.canvas, 0);
			item.canvas = null;
		}
		item.stream.close();
		item.viewHolder = null;
	}

	public void removeAllRenderers() {
		int count = getCount();
		for (int i = 0; i < count; ++i) {
			removeRenderer(getItem(i));
		}
		clear();
	}
}