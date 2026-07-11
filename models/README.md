## Modelo SLM

O modelo utilizado no protótipo foi:

- Qwen2.5-0.5B-Instruct quantizado em GGUF
- Arquivo esperado no Android: `model-q4.gguf`

Por causa do tamanho do arquivo, o modelo não está incluído no repositório.

Baixe o modelo a partir do link abaixo e copie para o dispositivo Android:

[link do modelo]

Depois copie para o Android:

```bash
adb shell mkdir -p /data/local/tmp/fitassist
adb push model-q4.gguf /data/local/tmp/fitassist/model-q4.gguf
