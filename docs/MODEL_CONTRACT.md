# DriveGuard — Model Contract

**Model file:** `app/src/main/assets/models/eye_model.tflite` (2,737,336 bytes)
**Format:** TFLite flatbuffer, identifier `TFL3` (schema v3)
**Subgraphs:** 1 · **Tensors:** 22 · **Operators:** 10 · **Signature defs:** 1

Everything below was determined by directly parsing the flatbuffer
(`tools/parse_tflite.py`) and by executing the model with TensorFlow 2.21
(`tf.lite.Interpreter`). Nothing is assumed from naming conventions.

## 1. Architecture (decoded from the graph)

A small sequential CNN — Keras-style `sequential_1` exported through the
signature-runner conversion path:

| # | Layer | Output shape | Notes |
|---|-------|--------------|-------|
| 0 | Input | `[1, 64, 64, 3]` | fp32, no quantization |
| 1 | Conv2D(3→32, 3×3, valid) + ReLU (fused) | `[1, 62, 62, 32]` | 896 weights + 32 bias |
| 2 | MaxPool 2×2, stride 2 | `[1, 31, 31, 32]` | |
| 3 | Conv2D(32→64, 3×3, valid) + ReLU (fused) | `[1, 29, 29, 64]` | 18,432 + 64 |
| 4 | MaxPool 2×2, stride 2 | `[1, 14, 14, 64]` | |
| 5 | Conv2D(64→128, 3×3, valid) + ReLU (fused) | `[1, 12, 12, 128]` | 73,728 + 128 |
| 6 | MaxPool 2×2, stride 2 | `[1, 6, 6, 128]` | |
| 7 | Flatten | `[1, 4608]` | 6·6·128 |
| 8 | Dense(4608→128) + ReLU | `[1, 128]` | 589,952 + 128 |
| 9 | Dense(128→1) | `[1, 1]` | 128 + 1 bias |
| 10 | Sigmoid | `[1, 1]` | |

**Total parameters:** 683,329 (weights 2,733,296 B ≈ 2.607 MB — matches the
file size with flatbuffer overhead).

## 2. Input

| Property | Value |
|---|---|
| Shape | `[1, 64, 64, 3]` (NHWC, batch fixed) |
| Datatype | **float32** |
| Value range | `[0, 1]` (empirically calibrated — see §5) |
| Channel order | RGB |
| Orientation | upright face/eye crop, top of head up |
| Quantization | none (scale 0.0, zero-point 0) |
| Normalization | `pixel/255.0`, no mean/std subtraction |

## 3. Output

| Property | Value |
|---|---|
| Shape | `[1, 1]` |
| Datatype | float32, sigmoid-activated probability |
| **Semantics** | **P(eye closed)** — verified empirically (§5) |
| Quantization | none |

`P_closed` is combined across eyes by the decision engine as
`max(P_left, P_right)` — drowsiness detection must be conservative: a single
closed eye is enough evidence to hold the "eyes closed" observation.

## 4. Preprocessing pipeline implemented in the app

```
CameraX ImageAnalysis (YUV_420_888)
  → ML Kit face detection (contour mode, bundled classifier)
  → per-eye ROI (ML Kit leftEyeOpenProbability / contour bbox, inset & expanded)
  → YUV→RGB conversion of ROI only (rotated for sensor orientation, mirrored for front lens)
  → bilinear resize to 64×64
  → uint8 RGB → float32 [0,1] (divide by 255)
  → Interpreter.run(input, output)
```

ML Kit's `leftEyeOpenProbability` is used only as a *gate* (reject wildly
unreliable crops); the supplied CNN is the sole eye-state classifier.
The app never substitutes another model.

## 5. How polarity was established (empirical)

1. **Architecture analysis:** single sigmoid logit; a driver-monitor CNN is
   more plausibly trained as P(closed) so that "closed" (the alert condition)
   is the positive class.
2. **Sliding-window search** over the provided `closed_01` probe image
   (960×635 JPEG): the maximum-activation windows (P = 1.000000, repeatedly)
   spatially coincide with the subject's closed eyes → output is P(closed).
3. **Open-eye confirmation:** Haar-detected open-eye crops from the probe
   set (`face_*`, `nat_*` series, 25 crops) all produce P ≈ 0.0.
4. **Canonical test vectors** (`app/src/androidTest/assets/model_test_vectors.json`,
   outputs generated with TensorFlow 2.21 CPU and verified on-device):
   `open_eye_like → 0.0000000`, `closed_eye_like → 0.9994673`.

## 6. Things that genuinely cannot be determined

* **Training dataset identity** — no metadata buffer, no `SavedModel` provenance,
  no dataset labels are embedded. The app therefore makes **no accuracy claims**.
* **Exact training normalization** beyond `[0,1]` scaling — mean/std subtraction
  would be visible in the graph as operators; none exist.
* **Whether the model was trained on grayscale-replicated or true-RGB eye
  crops** — the 3-channel input accepts either; the pipeline feeds true RGB.
* **Author, license, intended population** — unknown; documented as a limitation.

## 7. Inference configuration in the app

* **Delegate:** CPU with XNNPACK (default LiteRT path). GPU delegate is *not*
  enabled: model is tiny (~2.6 MB fp32), per-frame cost is dominated by the
  Dense(4608→128) matmul, which runs faster on CPU; GPU adds latency, memory
  and compatibility risk for no measured benefit at 64×64.
* **Interpreter reuse:** one `Interpreter` instance per process, created once,
  `close()`d on engine shutdown. Never constructed per frame.
* **Thread affinity:** 2 inference threads via `Interpreter.Options`.
* **Failure handling:** load failure / invoke failure degrade to
  `FrameResult.ClassifierFailed`; monitoring continues with a "reduced
  capability" state, never a crash.
