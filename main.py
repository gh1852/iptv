import requests
from bs4 import BeautifulSoup
import subprocess
import os

def get_stream_urls(channel):
    """
    Fetches the webpage and extracts stream URLs.
    """
    url = f"https://tonkiang.us/?iptv={channel}"
    urls = []
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    try:
        response = requests.get(url, headers=headers, timeout=10)
        response.raise_for_status()  # Raise an exception for bad status codes
        soup = BeautifulSoup(response.text, 'html.parser')
        # Find all tags with <table>
        for tag in soup.find_all('table'):
            tbas = tag.find_all('tba')
            if len(tbas) > 1:
                urls.append(tbas[1].get_text().strip())
    except requests.exceptions.RequestException as e:
        print(f"Error fetching {url}: {e}", flush=True)
    return urls

def analyze_stream(url):
    """
    Uses ffmpeg to analyze the stream metadata.
    """
    print(f"\n--- Analyzing: {url} ---", flush=True)
    try:
        # Using ffprobe is often better for just getting info
        # but ffmpeg with -i will also work and show metadata.
        # We use -hide_banner to clean up the output a bit.
        command = ['ffmpeg', '-hide_banner', '-i', url]
        
        # We run the command and capture stderr because ffmpeg prints info there.
        # We set a timeout to avoid hanging on unresponsive streams.
        result = subprocess.run(
            command, 
            capture_output=True, 
            text=True, 
            timeout=30,
            # Prevent creation of a new window on Windows
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        )
        
        # ffmpeg often exits with a non-zero code when it's just probing,
        # so we print stderr regardless of the return code.
        if result.stderr:
            print(result.stderr, flush=True)
            return True
        else:
            print("No metadata found or stream is not responding.", flush=True)
            return False

    except subprocess.TimeoutExpired:
        print("ffmpeg command timed out.", flush=True)
        return False
    except FileNotFoundError:
        print("ffmpeg not found. Please ensure it is installed and in your system's PATH.", flush=True)
        return False
    except Exception as e:
        print(f"An error occurred while analyzing stream: {e}", flush=True)
        return False

def main():
    """
    Main function to run the script.
    """
    m3u_content = ["#EXTM3U"]
    try:
        current_group_title = "Default"
        with open('list.txt', 'r', encoding='utf-8') as f:
            lines = [line.strip() for line in f if line.strip()]

        if not lines:
            print("list.txt is empty or not found.", flush=True)
            return

        for line in lines:
            if line.endswith(',#genre#'):
                current_group_title = line.replace(',#genre#', '').strip()
                print(f"\n--- Switched to group: {current_group_title} ---", flush=True)
                continue

            channel_name = line
            print(f"--- Searching for channel: {channel_name} ---", flush=True)
            stream_urls = get_stream_urls(channel_name)
            
            found_valid_stream = False
            if stream_urls:
                for stream_url in stream_urls:
                    if analyze_stream(stream_url):
                        # Format the M3U entry using the current group title
                        extinf = f'#EXTINF:-1 tvg-id="{channel_name}" tvg-name="{channel_name}" tvg-logo="https://live.fanmingming.cn/tv/{channel_name}.png" group-title="{current_group_title}",{channel_name}'
                        m3u_content.append(extinf)
                        m3u_content.append(stream_url)
                        found_valid_stream = True
            
            if not found_valid_stream:
                print(f"No valid stream found for {channel_name}", flush=True)

    except FileNotFoundError:
        print("Error: list.txt not found in the same directory as the script.", flush=True)
    except Exception as e:
        print(f"An unexpected error occurred: {e}", flush=True)
    finally:       
        # Write the M3U file
        if len(m3u_content) > 1:
            with open('playlist.m3u', 'w', encoding='utf-8') as f:
                f.write('\n'.join(m3u_content))
            print("\nSuccessfully created playlist.m3u", flush=True)
        else:
            print("\nNo valid streams were found to create a playlist.", flush=True)

if __name__ == "__main__":
    main()