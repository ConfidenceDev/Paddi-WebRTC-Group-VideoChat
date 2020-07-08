package tv.paddi.android.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import org.w3c.dom.Text;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import co.paystack.android.Paystack;
import co.paystack.android.PaystackSdk;
import co.paystack.android.Transaction;
import co.paystack.android.exceptions.ExpiredAccessCodeException;
import co.paystack.android.model.Card;
import co.paystack.android.model.Charge;
import pl.bclogic.pulsator4droid.library.PulsatorLayout;
import tv.paddi.android.R;
import tv.paddi.android.utils.NetworkConnection;

public class PayDialog {

    private Dialog dialog;
    private Window window;
    private FirebaseFirestore firebaseFirestore;
    private CollectionReference mainRef;
    private String userId;
    private String dollarAmt = null, cost = null, pref;
    private EditText mEmailField, mCardNameField, mCardNumberField,
            mMonthField, mYearField, mCVVField;
    private Button mPayBtn;
    private TextView mAmtView;
    private Card card;
    private Charge charge;
    private PulsatorLayout mPulse;
    private Transaction transact;
    private NetworkConnection networkConnection;
    private ImageView mCloseBtn;
    private AppCompatActivity context;
    private boolean isComplete = false;

    public PayDialog(AppCompatActivity context, String userId, String pref) {
        this.context = context;
        dialog = new Dialog(this.context);
        window = dialog.getWindow();
        firebaseFirestore = FirebaseFirestore.getInstance();
        networkConnection = new NetworkConnection();
        mainRef = firebaseFirestore.collection("Paddi").document("Files").collection("Users");
        this.userId = userId;
        this.pref = pref;
    }

    public boolean payAdd() {
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.7f);
        }
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dialog_pay);

        mEmailField = dialog.findViewById(R.id.edit_email_address);
        mCardNameField = dialog.findViewById(R.id.edit_card_name);
        mCardNumberField = dialog.findViewById(R.id.edit_card_number);
        mMonthField = dialog.findViewById(R.id.edit_expiry_month);
        mYearField = dialog.findViewById(R.id.edit_expiry_year);
        mCVVField = dialog.findViewById(R.id.edit_cvv);
        mPayBtn = dialog.findViewById(R.id.pay_button);
        mPulse = dialog.findViewById(R.id.payPulse);
        mAmtView = dialog.findViewById(R.id.amtView);
        mCloseBtn = dialog.findViewById(R.id.closePayBtn);


        try {
            firebaseFirestore.collection("Media").document("Information")
                    .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                        @Override
                        public void onEvent(@Nullable DocumentSnapshot documentSnapshot,
                                            @Nullable FirebaseFirestoreException e) {
                            if (documentSnapshot != null) {
                                if (documentSnapshot.exists()) {
                                    dollarAmt = documentSnapshot.getString("dollarAmt");
                                    cost = documentSnapshot.getString("cost");
                                }
                            }
                        }
                    });

            mAmtView.setText(context.getResources().getString(R.string.pay_cost));

            mCardNumberField.addTextChangedListener(new TextWatcher() {
                private static final char space = ' ';

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 0 && (s.length() % 5) == 0) {
                        char c = s.charAt(s.length() - 1);
                        if (space == c) {
                            s.delete(s.length() - 1, s.length());
                        }
                    }
                    if (s.length() > 0 && (s.length() % 5) == 0) {
                        char c = s.charAt(s.length() - 1);
                        if (Character.isDigit(c) && TextUtils.split(s.toString(),
                                String.valueOf(space)).length <= 3) {
                            s.insert(s.length() - 1, String.valueOf(space));
                        }
                    }
                }
            });

            mPayBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (networkConnection.isConnected(context)) {
                        if (!validateForm()) {
                            return;
                        }

                        final String cardNumber = mCardNumberField.getText().toString().trim();
                        final int expiryMonth = Integer.parseInt(mMonthField.getText().toString().trim());
                        final int expiryYear = Integer.parseInt(mYearField.getText().toString().trim());
                        final String cvv = mCVVField.getText().toString().trim();

                        /*String cardNumber = "507850785078507812";
                        int expiryMonth = 10; //any month in the future
                        int expiryYear = 21; // any year in the future
                        String cvv = "081";*/

                        card = new Card(cardNumber, expiryMonth, expiryYear, cvv);

                        if (card.isValid()) {
                            disableInit();
                            performCharge();

                        } else {
                            Toast.makeText(context,
                                    context.getResources().getString(R.string.card_not_valid), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(context,
                                context.getResources().getString(R.string.no_internet),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });

            mCloseBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeIntent();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();

        return isComplete;
    }

    /**
     * Method to perform the charging of the card
     */
    private void performCharge() {
        if (dollarAmt == null || TextUtils.isEmpty(dollarAmt)) {
            dollarAmt = "360";
        }

        if (cost == null || TextUtils.isEmpty(cost)) {
            cost = "2";
        }

        String ver = String.valueOf(Integer.parseInt(dollarAmt) * Integer.parseInt(cost));
        //create a Charge object
        charge = new Charge();
        //set the card to charge
        charge.setCard(card);
        //charge.setCurrency("USD");

        mPulse.setVisibility(View.VISIBLE);
        mPulse.start();
        //call this method if you set a plan
        String mainVal = ver.concat("00");
        charge.setEmail("stackdriveinc@gmail.com"); //dummy email address
        charge.setAmount(Integer.parseInt(mainVal)); //test amount
        charge.setReference("PaddiAndroid_" + Calendar.getInstance().getTimeInMillis());

        PaystackSdk.chargeCard(context, charge, new Paystack.TransactionCallback() {
            @Override
            public void onSuccess(Transaction transaction) {
                // This is called only after transaction is deemed successful.
                // Retrieve the transaction, and send its reference to your server
                // for verification.

                transact = transaction;
                update();
            }

            @Override
            public void beforeValidate(Transaction transaction) {
                // This is called only before requesting OTP.
                // Save reference so you may send to server. If
                // error occurs with OTP, you should still verify on server.

                //PayActivity.this.transaction = transaction;
            }

            @Override
            public void onError(Throwable error, Transaction transaction) {
                //handle error here
                if (error instanceof ExpiredAccessCodeException) {
                    return;
                }
                isComplete = false;
                mPulse.stop();
                mPulse.setVisibility(View.GONE);
                enableInit();
                transact = transaction;
                if (transact != null) {
                    Toast.makeText(context, context.getResources().getString(R.string.transaction_err)
                            + transaction.getReference(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, context.getResources().getString(R.string.transaction_failed)
                            , Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void update() {
        final Date utc = new Date(System.currentTimeMillis());

        Map<String, Object> setMap = new HashMap<>();
        setMap.put(pref, utc);

        mainRef.document(userId).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    if (task.getResult().exists()) {
                        mainRef.document(userId).update(setMap)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {

                                        isComplete = true;
                                        Toast.makeText(context, context.getResources().getString(R.string.completed),
                                                Toast.LENGTH_SHORT).show();

                                        mPulse.stop();
                                        mPulse.setVisibility(View.GONE);
                                        enableInit();
                                        transact = null;
                                        closeIntent();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                isComplete = false;

                                Toast.makeText(context, context.getResources().getString(R.string.err) +
                                        e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        mainRef.document(userId).set(setMap)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {

                                        isComplete = true;
                                        Toast.makeText(context, context.getResources().getString(R.string.completed),
                                                Toast.LENGTH_SHORT).show();

                                        mPulse.stop();
                                        mPulse.setVisibility(View.GONE);
                                        enableInit();
                                        transact = null;
                                        closeIntent();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                isComplete = false;

                                Toast.makeText(context, context.getResources().getString(R.string.err) +
                                        e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    Toast.makeText(context,
                            context.getResources().getString(R.string.error), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private boolean validateForm() {
        boolean valid = true;

        String email = mEmailField.getText().toString();
        if (TextUtils.isEmpty(email)) {
            mEmailField.setError(context.getResources().getString(R.string.required));
            valid = false;
        } else {
            mEmailField.setError(null);
        }

        String cardName = mCardNameField.getText().toString();
        if (TextUtils.isEmpty(cardName)) {
            mCardNameField.setError(context.getResources().getString(R.string.required));
            valid = false;
        } else {
            mCardNameField.setError(null);
        }

        String cardNumber = mCardNumberField.getText().toString();
        if (TextUtils.isEmpty(cardNumber)) {
            mCardNumberField.setError(context.getResources().getString(R.string.required));
            valid = false;
        } else {
            mCardNumberField.setError(null);
        }

        String expiryMonth = mMonthField.getText().toString();
        if (TextUtils.isEmpty(expiryMonth)) {
            mMonthField.setError(context.getResources().getString(R.string.required));
            valid = false;
        } else {
            mMonthField.setError(null);
        }

        String expiryYear = mYearField.getText().toString();
        if (TextUtils.isEmpty(expiryYear)) {
            mYearField.setError(context.getResources().getString(R.string.required));
            valid = false;
        } else {
            mYearField.setError(null);
        }

        String cvv = mCVVField.getText().toString();
        if (TextUtils.isEmpty(cvv)) {
            mCVVField.setError(context.getResources().getString(R.string.required));
            valid = false;
        } else {
            mCVVField.setError(null);
        }

        return valid;
    }

    private void disableInit() {
        mCardNumberField.setEnabled(false);
        mMonthField.setEnabled(false);
        mYearField.setEnabled(false);
        mCVVField.setEnabled(false);
        mPayBtn.setEnabled(false);
    }

    private void enableInit() {
        mCardNumberField.setEnabled(true);
        mMonthField.setEnabled(true);
        mYearField.setEnabled(true);
        mCVVField.setEnabled(true);
        mPayBtn.setEnabled(true);
    }

    private void closeIntent() {
        if (transact != null) {
            AlertDialog.Builder tipsAlert = new AlertDialog.Builder(context);
            tipsAlert.setMessage(context.getResources().getString(R.string.cancel_payment))
                    .setPositiveButton(context.getResources().getString(R.string.wait), null)
                    .setNegativeButton(context.getResources().getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    transact = null;
                                    closeDialog();
                                }
                            });
            AlertDialog start_builder = tipsAlert.create();
            start_builder.show();

        } else {
            closeDialog();
        }
    }

    private void closeDialog() {
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.dismiss();
    }
}
