/**
 * Camera + QR decoding wrapper built on getUserMedia and jsQR.
 *
 * States: placeholder -> loading -> active <-> success / error.
 * The page decides when to show success or error; this class only reports
 * decoded text through onScan and exposes setState() for page feedback.
 */

const STORAGE_CAMERAS = 'venuity_cameras';
const STORAGE_SELECTED = 'venuity_selected_camera';

function loadStoredCameras() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_CAMERAS) || '[]');
    return Array.isArray(parsed) ? parsed.filter((camera) => camera && camera.id) : [];
  } catch (error) {
    return [];
  }
}

function friendlyCameraError(error) {
  const name = error && error.name;
  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return 'Camera permission was denied. Allow camera access and try again.';
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    return 'No camera was found. Connect a camera and try again.';
  }
  if (name === 'NotReadableError' || name === 'TrackStartError') {
    return 'The camera is already in use by another application.';
  }
  if (name === 'OverconstrainedError' || name === 'ConstraintNotSatisfiedError') {
    return 'The selected camera is not available. Choose another camera.';
  }
  if (error && error.message) return error.message;
  return 'Unable to start the camera. Check permissions and try again.';
}

export class QrScanner {
  constructor({ video, canvas, onScan, onStateChange, onCamerasChange }) {
    if (!video || !canvas) throw new Error('QrScanner requires video and canvas elements.');
    this.video = video;
    this.canvas = canvas;
    this.onScan = onScan || (() => {});
    this.onStateChange = onStateChange || (() => {});
    this.onCamerasChange = onCamerasChange || (() => {});

    this.cameras = loadStoredCameras();
    this.selectedCamera = localStorage.getItem(STORAGE_SELECTED) || '';
    this.active = false;
    this.state = 'placeholder';
    this.processing = false;
    this.pausedUntil = 0;
    this.lastFrame = 0;
    this.raf = null;
    this.stream = null;
    this.resumeTimer = null;
    this.boundLoop = () => this.loop();
    this.onDeviceChange = () => {
      if (!this.active) {
        this.refreshCameras(false).catch(() => {});
      }
    };

    if (navigator.mediaDevices && navigator.mediaDevices.addEventListener) {
      navigator.mediaDevices.addEventListener('devicechange', this.onDeviceChange);
    }
  }

  setState(nextState) {
    if (this.state === nextState) return;
    this.state = nextState;
    this.onStateChange(nextState);
  }

  setSelectedCamera(deviceId) {
    this.selectedCamera = deviceId || '';
    if (this.selectedCamera) localStorage.setItem(STORAGE_SELECTED, this.selectedCamera);
    else localStorage.removeItem(STORAGE_SELECTED);
    this.onCamerasChange(this.cameras, this.selectedCamera);
  }

  async refreshCameras(requestPermission = false) {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      throw new Error('Camera access is not available. Use a modern browser over HTTPS or localhost.');
    }

    if (requestPermission) {
      const temporary = await navigator.mediaDevices.getUserMedia({ video: true });
      temporary.getTracks().forEach((track) => track.stop());
    }

    const devices = await navigator.mediaDevices.enumerateDevices();
    this.cameras = devices
      .filter((device) => device.kind === 'videoinput')
      .map((device, index) => ({
        id: device.deviceId,
        label: device.label || `Camera ${index + 1}`,
      }));

    localStorage.setItem(STORAGE_CAMERAS, JSON.stringify(this.cameras));

    if (this.cameras.length === 0) {
      this.selectedCamera = '';
      localStorage.removeItem(STORAGE_SELECTED);
    } else if (!this.cameras.some((camera) => camera.id === this.selectedCamera)) {
      this.selectedCamera = this.cameras[0].id;
      localStorage.setItem(STORAGE_SELECTED, this.selectedCamera);
    }

    this.onCamerasChange(this.cameras, this.selectedCamera);
    return this.cameras;
  }

  async start() {
    if (this.active) return;
    if (typeof window.jsQR !== 'function') {
      throw new Error('The QR decoder did not load. Refresh the page and try again.');
    }

    this.setState('loading');
    try {
      await this.refreshCameras(true);
      if (!this.selectedCamera) {
        throw new Error('No camera was found. Connect a camera and try again.');
      }

      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          deviceId: { exact: this.selectedCamera },
          width: { ideal: 1280 },
          height: { ideal: 720 },
          frameRate: { ideal: 30 },
        },
        audio: false,
      });

      this.stream = stream;
      const track = stream.getVideoTracks()[0];
      if (track) {
        track.onended = () => {
          if (this.active) this.stop();
        };
      }

      this.video.srcObject = stream;
      await this.video.play();

      this.active = true;
      this.processing = false;
      this.pausedUntil = 0;
      this.lastFrame = 0;
      this.setState('active');
      this.raf = requestAnimationFrame(this.boundLoop);
    } catch (error) {
      this.stopTracks();
      this.setState('placeholder');
      throw new Error(friendlyCameraError(error));
    }
  }

  stop() {
    this.active = false;
    this.processing = false;
    this.pausedUntil = 0;
    if (this.raf !== null) {
      cancelAnimationFrame(this.raf);
      this.raf = null;
    }
    if (this.resumeTimer) {
      clearTimeout(this.resumeTimer);
      this.resumeTimer = null;
    }
    this.stopTracks();
    if (this.video) this.video.srcObject = null;
    this.setState('placeholder');
  }

  stopTracks() {
    if (!this.stream) return;
    this.stream.getTracks().forEach((track) => {
      track.onended = null;
      track.stop();
    });
    this.stream = null;
  }

  pause(milliseconds = 2000) {
    this.pausedUntil = Date.now() + Math.max(0, milliseconds);
  }

  resume() {
    this.pausedUntil = 0;
    this.processing = false;
    if (this.active) this.setState('active');
  }

  showSuccessFor(milliseconds = 2200) {
    this.setState('success');
    if (this.resumeTimer) clearTimeout(this.resumeTimer);
    this.resumeTimer = setTimeout(() => {
      this.resumeTimer = null;
      if (this.active && this.state === 'success') this.setState('active');
    }, milliseconds);
  }

  loop() {
    if (!this.active) return;
    this.raf = requestAnimationFrame(this.boundLoop);

    const now = performance.now();
    if (now - this.lastFrame < 120 || Date.now() < this.pausedUntil || this.processing) return;

    const image = this.capture();
    if (!image) return;

    this.lastFrame = now;
    this.processing = true;
    try {
      const result = window.jsQR(image.data, image.width, image.height, {
        inversionAttempts: 'dontInvert',
      });
      if (result && result.data) {
        const value = String(result.data).trim();
        if (value) {
          this.pause(2200);
          Promise.resolve(this.onScan(value)).catch((error) => {
            console.warn('[scanner] onScan handler failed', error);
          });
        }
      }
    } catch (error) {
      console.warn('[scanner] decode failed', error);
    } finally {
      this.processing = false;
    }
  }

  capture() {
    if (!this.video || !this.canvas) return null;
    if (this.video.readyState < 2 || this.video.videoWidth === 0) return null;

    const context = this.canvas.getContext('2d', { willReadFrequently: true });
    if (!context) return null;

    const size = Math.min(this.video.videoWidth, this.video.videoHeight);
    const sourceX = (this.video.videoWidth - size) / 2;
    const sourceY = (this.video.videoHeight - size) / 2;
    const targetSize = Math.min(size, 640);

    this.canvas.width = targetSize;
    this.canvas.height = targetSize;
    context.filter = 'contrast(115%) grayscale(100%)';
    context.drawImage(this.video, sourceX, sourceY, size, size, 0, 0, targetSize, targetSize);
    context.filter = 'none';

    return context.getImageData(0, 0, targetSize, targetSize);
  }

  destroy() {
    this.stop();
    if (navigator.mediaDevices && navigator.mediaDevices.removeEventListener) {
      navigator.mediaDevices.removeEventListener('devicechange', this.onDeviceChange);
    }
  }
}
