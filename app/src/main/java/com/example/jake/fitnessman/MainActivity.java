package com.example.jake.fitnessman;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataSourcesResult;

import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements OnDataPointListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    /*
    * Things I can do:
    *  - add some slight amount of persistence so on loading it first shows the last number of steps gathered and keep a goal
    *  - add dialogues when people have been sitting still for 5 minutes to yell at them
    *
    * */

    // We use these to make sure that we have an API key for google FIT or move to authFailed or authInprogress
    private static final int REQUEST_OAUTH = 1;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    // the actual ApiClient object
    private GoogleApiClient mApiClient;

    // variables for setting the specific steps
    public TextView stepsTextView;
    private String stepsMessage = "Steps - 0";

    //variables for the goal steps
    public TextView goalStepsView;
    public Integer goalSteps = 100;
    public Integer stepIncrease = 1;
    public Integer stepAcceleration = 50;
    public String goalMessage = "Goal - " + goalSteps;
    public Button goalButton;
    public boolean firstLoad = true;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // setting up our buttons and views so we can manipulate them

        stepsTextView = (TextView) findViewById(R.id.stepsText);
        stepsTextView.setText(stepsMessage);

        // setting up the goal button so that we can change
        // the number of goal steps if we wnt
        goalButton = (Button) findViewById(R.id.goalButton);
        // adding a listener so that we can use an onClick method
        goalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder goalChanger = new AlertDialog.Builder(MainActivity.this);
                goalChanger.setTitle("Change Goal");
                // Set up the input
                final EditText input = new EditText(MainActivity.this);
                // Specify the type of input expected - Here we want a Number so that the goal doesn't crash
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                goalChanger.setView(input);
                goalChanger.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // they have decided not to change the goal
                        dialog.cancel();
                    }
                });
                goalChanger.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // grab the input
                        Integer possibleGoalSteps = Integer.parseInt(input.getText().toString());
                        // check to see if the new goal is better than the original goal
                        // if it isn't, then don't let them change it
                        if (possibleGoalSteps > goalSteps){
                            goalSteps = possibleGoalSteps;
                            goalMessage = "Goal: " + goalSteps;
                            goalStepsView.setText(goalMessage);
                        }
                        else{
                            Toast.makeText(getApplicationContext(), "What!? How lazy are you? Set a higher goal!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                goalChanger.show();

            }
        });

        goalStepsView = (TextView) findViewById(R.id.goalText);
        goalStepsView.setText(goalMessage);



        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }
        //creating the google api client
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.SENSORS_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }



    @Override
    protected void onStart() {
        super.onStart();
        mApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Fitness.SensorsApi.remove( mApiClient, this )
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mApiClient.disconnect();
                        }
                    }
                });
    }

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        //we want to get the fitness data with our sensor and we want to collect data every second
        SensorRequest request = new SensorRequest.Builder()
                .setDataSource( dataSource )
                .setDataType( dataType )
                .setSamplingRate( 1, TimeUnit.SECONDS )
                .build();

        Fitness.SensorsApi.add(mApiClient, request, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.e("GoogleFit", "We have successfully added a sensor");
                        } else {
                            Log.e("GoogleFit", "addin status: " + status.getStatusMessage());
                        }
                    }
                });
    }

    @Override
    public void onConnected(Bundle bundle) {
        // getting the data cumulatively
        DataSourcesRequest dataSourceRequest = new DataSourcesRequest.Builder()
                .setDataTypes( DataType.TYPE_STEP_COUNT_CUMULATIVE )
                .setDataSourceTypes( DataSource.TYPE_RAW )
                .build();

        ResultCallback<DataSourcesResult> dataSourcesResultCallback = new ResultCallback<DataSourcesResult>() {
            @Override
            public void onResult(DataSourcesResult dataSourcesResult) {
                // find the data that we want!
                for( DataSource dataSource : dataSourcesResult.getDataSources() ) {
                    if( DataType.TYPE_STEP_COUNT_CUMULATIVE.equals( dataSource.getDataType() ) ) {
                        registerFitnessDataListener(dataSource, DataType.TYPE_STEP_COUNT_CUMULATIVE);
                    }
                }
            }
        };

        Fitness.SensorsApi.findDataSources(mApiClient, dataSourceRequest)
                .setResultCallback(dataSourcesResultCallback);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // uh oh - make sure that we have a key
        if( !authInProgress ) {
            try {
                authInProgress = true;
                connectionResult.startResolutionForResult( MainActivity.this, REQUEST_OAUTH );
            } catch(IntentSender.SendIntentException e ) {
                Log.e( "GoogleFit", "sendingIntentException " + e.getMessage() );
            }
        } else {
            Log.e( "GoogleFit", "authInProgress" );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if( requestCode == REQUEST_OAUTH ) {
            authInProgress = false;
            if( resultCode == RESULT_OK ) {
                if( !mApiClient.isConnecting() && !mApiClient.isConnected() ) {
                    mApiClient.connect();
            }
            } else if( resultCode == RESULT_CANCELED ) {
                Log.e( "GoogleFit", "RESULT_CANCELED" );

            }
        } else {
            Log.e("GoogleFit", "requestCode NOT request_oauth");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }
    //whenever we get a new dataPoint
    @Override
    public void onDataPoint(DataPoint dataPoint) {
        Log.e("Google Fit","We made it into onDataPoint");
        for( final Field field : dataPoint.getDataType().getFields() ) {
            final Value value = dataPoint.getValue( field );
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stepsMessage = "Steps: " + value;
                    stepsTextView.setText(stepsMessage);
                    if (firstLoad){
                        firstLoad = false;
                        goalSteps = value.asInt() + 100;
                    }

                    if (checkGoal(value.asInt()) && !firstLoad){
                        Toast.makeText(getApplicationContext(), "Goal of " + goalSteps + " met!", Toast.LENGTH_SHORT).show();
                        stepIncrease++;
                        goalSteps = value.asInt() + 100 + stepIncrease*stepAcceleration;

                    }
                    goalMessage = "Goal: " + goalSteps;
                    goalStepsView.setText(goalMessage);
                }
            });
        }
    }
    // for the first load
    protected boolean checkGoal(Integer steps){
        return steps >= goalSteps;
    }
}
