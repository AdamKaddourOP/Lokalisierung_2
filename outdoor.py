import json
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from scipy.interpolate import interp1d
from geopy.distance import geodesic
from datetime import datetime

def load_json_data(filepath):
    with open(filepath, 'r') as file:
        data = json.load(file)
    return pd.DataFrame(data)

def interpolate_ground_truth(reference_points, start_time, end_time, frequency='1s'):
    timestamps = pd.date_range(start=start_time, end=end_time, freq=frequency)
    df_ref = pd.DataFrame(reference_points, columns=['latitude', 'longitude'])
    
    f_lat = interp1d(np.linspace(0, len(df_ref) - 1, num=len(df_ref)), df_ref['latitude'], kind='linear')
    f_lon = interp1d(np.linspace(0, len(df_ref) - 1, num=len(df_ref)), df_ref['longitude'], kind='linear')
    
    interpolated_lat = f_lat(np.linspace(0, len(df_ref) - 1, num=len(timestamps)))
    interpolated_lon = f_lon(np.linspace(0, len(df_ref) - 1, num=len(timestamps)))
    
    return pd.DataFrame({'timestamp': timestamps, 'latitude': interpolated_lat, 'longitude': interpolated_lon})

def calculate_position_errors(app_data, reference_data):
    errors = []
    app_data['timestamp'] = pd.to_datetime(app_data['timestamp'])
    
    for point in app_data.itertuples():
        closest_ref_point = reference_data.iloc[(reference_data['timestamp'] - point.timestamp).abs().argsort()[0]]
        distance = geodesic((point.latitude, point.longitude), (closest_ref_point['latitude'], closest_ref_point['longitude'])).meters
        errors.append(distance)
    
    return np.array(errors)

def create_cdf_plot(errors):
    sorted_errors = np.sort(errors)
    y_values = np.arange(1, len(sorted_errors) + 1) / len(sorted_errors)
    
    plt.figure(figsize=(10, 6))
    plt.plot(sorted_errors, y_values, marker='o', linestyle='-', markersize=5, label='CDF')
    
    ci_50 = np.percentile(errors, [25, 75])
    ci_95 = np.percentile(errors, [2.5, 97.5])
    
    plt.title('Cumulative Distribution Function (CDF) high accuracy outdoor')
    plt.xlabel('Error (Meters)')
    plt.ylabel('Cumulative Probability')
    
    plt.axhline(y=0.25, color='g', linestyle='--', label='50% CI Lower Bound')
    plt.axhline(y=0.75, color='g', linestyle='--', label='50% CI Upper Bound')
    plt.axhline(y=0.025, color='r', linestyle=':', label='95% CI Lower Bound')
    plt.axhline(y=0.975, color='r', linestyle=':', label='95% CI Upper Bound')
    
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    
    print(f"50% Confidence Interval: {ci_50} meters")
    print(f"95% Confidence Interval: {ci_95} meters")
    print(f"Mean Error: {np.mean(errors):.2f} meters")
    print(f"Median Error: {np.median(errors):.2f} meters")
    
    plt.show()

def main():
    reference_points = [
        (51.44545, 7.26042), (51.44538, 7.26013), (51.44532, 7.25991), (51.44515, 7.26003),
        (51.445, 7.26015), (51.44473, 7.26039), (51.44461, 7.26059), (51.4447, 7.26093)
    ]
    
    app_data = load_json_data('./outhigh_2.json')
    start_time = pd.to_datetime(app_data['timestamp'].min())
    end_time = pd.to_datetime(app_data['timestamp'].max())
    
    reference_data = interpolate_ground_truth(reference_points, start_time, end_time)
    errors = calculate_position_errors(app_data, reference_data)
    
    create_cdf_plot(errors)
    
    print("Script executed successfully!")

if __name__ == "__main__":
    main()
