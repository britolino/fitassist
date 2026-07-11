# FitAssist — Código do Projeto

Assistente embarcado para treinos de endurance com Android embarcado, sensores BLE, integração Garmin/Node-RED, modelo fisiológico simples e SLM local para geração de mensagens curtas de coaching.

O objetivo do sistema é transformar telemetria de treino em recomendações simples e seguras, priorizando execução local, baixa latência e funcionamento mesmo com conectividade limitada.

---

## 1. Estrutura do código

A pasta `Codigo` deve conter, preferencialmente, a seguinte organização:

```text
Codigo/
├── app-android/
│   └── Aplicativo Android embarcado do FitAssist
├── backend/
│   └── Scripts Python para coleta e envio de dados ao Node-RED
├── node-red/
│   └── Fluxos ou instruções de configuração do Node-RED
├── models/
│   └── Modelos locais usados pelo SLM
├── docs/
│   └── Diagramas, imagens e material de apoio
├── README.md
└── .env.example
```

### `app-android/`

Contém o aplicativo Android desenvolvido em Kotlin com Jetpack Compose.

Principais responsabilidades:

- exibir o dashboard do FitAssist;
- conectar-se aos sensores BLE;
- receber frequência cardíaca, potência e cadência;
- buscar contexto do Garmin via endpoint HTTP do Node-RED;
- calcular o estado estimado de Body Battery/capacidade;
- aplicar regras determinísticas de decisão;
- chamar o SLM local para verbalizar a recomendação;
- validar a resposta da LLM/SLM;
- aplicar fallback seguro quando necessário;
- futuramente reproduzir a recomendação por áudio via TTS.

Componentes principais:

- `MainActivity.kt`: tela principal, permissões, dashboard, BLE e chamada ao SLM;
- `BleSensorManager`: conexão GATT com sensores BLE;
- `DashboardState`: estado exibido na interface;
- `calculateCoachDecision`: regra determinística de coaching;
- `buildPrompt`: geração do prompt enviado ao SLM;
- `runLocalSlm`: execução local do `llama-cli`;
- `safeCoachMessage`: validação da resposta da LLM;
- `fallbackMessage`: mensagem segura caso a saída da LLM seja inválida.

---

### `backend/`

Contém scripts Python usados para coletar dados externos e alimentar o middleware local.

Principais responsabilidades:

- autenticar no Garmin Connect;
- coletar dados como Body Battery, charged, drained e último evento;
- estruturar os dados em JSON;
- enviar os dados ao Node-RED por HTTP;
- registrar logs ou arquivos intermediários para depuração.

Exemplo de fluxo:

```text
Garmin Connect → Python → Node-RED → Android
```

---

### `node-red/`

Contém o fluxo do Node-RED, ou instruções para recriá-lo.

Principais responsabilidades:

- receber dados do script Python;
- armazenar localmente o último estado do Body Battery;
- disponibilizar endpoint HTTP para o app Android;
- permitir futura integração com MQTT, banco local ou plataforma IoT.

Endpoints esperados:

```text
POST /garmin/body-battery
GET  /dashboard/current
```

Exemplo de resposta esperada em `/dashboard/current`:

```json
{
  "source": "garmin_connect",
  "date": "2026-06-08",
  "current_body_battery": 26,
  "charged": 0,
  "drained": 49,
  "feedback_level": "HIGH",
  "last_event_impact": -7,
  "last_event_feedback": "MAINTAINING_AEROBIC"
}
```

---

### `models/`

Contém os modelos locais usados pelo SLM.

No protótipo foi utilizado um modelo pequeno quantizado, executado localmente com `llama.cpp`.

Exemplo:

```text
model-q4.gguf
```

O modelo deve ser copiado para o Android embarcado em:

```text
/data/local/tmp/fitassist/model-q4.gguf
```

---

## 2. Dependências

### 2.1. Android

Requisitos:

- Android Studio;
- Kotlin;
- Jetpack Compose;
- dispositivo Android embarcado ou Raspberry Pi 5 com Android;
- permissões de Bluetooth e localização;
- rede local para acessar o Node-RED.

Permissões usadas no app:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

Dependências principais do app:

- `androidx.activity:activity-compose`;
- `androidx.compose.material3`;
- `okhttp3`;
- APIs Android Bluetooth/BLE;
- `TextToSpeech`, caso o áudio seja habilitado.

---

### 2.2. Python

Requisitos:

- Python 3.10 ou superior;
- ambiente virtual recomendado;
- bibliotecas para acesso ao Garmin Connect;
- biblioteca `requests`;
- `python-dotenv`, caso as credenciais sejam lidas de `.env`.

Instalação sugerida:

```bash
cd Codigo/backend

python3 -m venv .venv
source .venv/bin/activate

pip install requests python-dotenv garminconnect
```

Caso o projeto use um arquivo `requirements.txt`, instalar com:

```bash
pip install -r requirements.txt
```

---

### 2.3. Node-RED

Instalação sugerida:

```bash
npm install -g --unsafe-perm node-red
```

Executar:

```bash
node-red
```

A interface normalmente fica disponível em:

```text
http://localhost:1880
```

O Android deve acessar o IP da máquina onde o Node-RED está rodando. Exemplo:

```text
http://192.168.2.83:1880/dashboard/current
```

No código Android, esse endereço aparece em:

```kotlin
FitAssistApp(
    nodeRedUrl = "http://192.168.2.83:1880/dashboard/current"
)
```

Ajuste o IP de acordo com sua rede local.

---

### 2.4. SLM local com llama.cpp

O protótipo usa `llama.cpp` compilado para Android ARM64.

Arquivos esperados no Android:

```text
/data/local/tmp/fitassist/llama-cli
/data/local/tmp/fitassist/model-q4.gguf
/data/local/tmp/fitassist/*.so
/data/local/tmp/fitassist/libomp.so
```

Exemplo de execução manual no Android via `adb shell`:

```bash
LD_LIBRARY_PATH=/data/local/tmp/fitassist \
/data/local/tmp/fitassist/llama-cli \
-m /data/local/tmp/fitassist/model-q4.gguf \
-p "Task: Rewrite the mandatory coaching decision as one short natural Portuguese sentence." \
-n 30 \
-t 4 \
--no-mmap \
--temp 0.0
```

---

## 3. Configuração do ambiente

### 3.1. Arquivo `.env`

Crie um arquivo `.env` dentro da pasta `backend/`.

Exemplo:

```env
GARMIN_EMAIL=seu_email_garmin@example.com
GARMIN_PASSWORD=sua_senha_garmin
NODE_RED_URL=http://localhost:1880/garmin/body-battery
```

Não envie o arquivo `.env` na entrega pública. Inclua apenas um `.env.example`.

Exemplo de `.env.example`:

```env
GARMIN_EMAIL=
GARMIN_PASSWORD=
NODE_RED_URL=http://localhost:1880/garmin/body-battery
```

### 3.2. Chaves, senhas e credenciais necessárias

Para executar o protótipo completo, são necessárias:

```text
GARMIN_EMAIL       e-mail da conta Garmin Connect
GARMIN_PASSWORD    senha da conta Garmin Connect
NODE_RED_URL       endpoint local para envio dos dados ao Node-RED
```

No estado atual do protótipo, não há chave obrigatória de API paga.

Caso futuramente seja usada uma API externa de LLM, clima, mapas ou nuvem IoT, as chaves devem ser adicionadas ao `.env`, por exemplo:

```env
OPENAI_API_KEY=
WEATHER_API_KEY=
MQTT_USERNAME=
MQTT_PASSWORD=
```

---

## 4. Configuração do hardware

### 4.1. Dispositivo Android embarcado

O protótipo foi pensado para execução em Android embarcado, com tela local, por exemplo Raspberry Pi 5 com Android e tela TFT.

Antes de executar:

1. ligue o dispositivo Android;
2. conecte-o à mesma rede do computador com Node-RED;
3. habilite Bluetooth;
4. conceda permissões de localização e Bluetooth ao aplicativo FitAssist;
5. confirme o IP do computador que roda o Node-RED.

---

### 4.2. Sensores BLE

Sensores usados no protótipo:

```text
Garmin 970 / relógio Garmin
- Heart Rate Broadcast
- Serviço BLE 0x180D
- Frequência cardíaca em tempo real

ThinkRider / Trainer
- Cycling Power Service 0x1818
- Cycling Speed and Cadence Service 0x1816
- Potência e cadência
```

Antes de iniciar o app:

1. ative o broadcast de frequência cardíaca no Garmin;
2. ligue o trainer ou sensor de potência;
3. mantenha os sensores próximos ao dispositivo Android;
4. abra o app e aguarde a mensagem “Sensores ativos”.

---

## 5. Ordem correta para executar o sistema

### Passo 1 — Iniciar o Node-RED

No computador que será usado como middleware local:

```bash
node-red
```

Verifique se a interface abre em:

```text
http://localhost:1880
```

Importe ou configure os fluxos necessários:

```text
POST /garmin/body-battery
GET  /dashboard/current
```

---

### Passo 2 — Rodar o backend Python

Em outro terminal:

```bash
cd Codigo/backend
source .venv/bin/activate
python garmin_to_nodered.py
```

O script deve:

1. autenticar no Garmin Connect;
2. coletar o Body Battery;
3. enviar os dados para o Node-RED.

Teste o endpoint:

```bash
curl http://localhost:1880/dashboard/current
```

A resposta deve conter Body Battery, charged, drained e último evento.

---

### Passo 3 — Preparar o SLM no Android

Copie os arquivos necessários para o dispositivo Android:

```bash
adb shell mkdir -p /data/local/tmp/fitassist

adb push llama-cli /data/local/tmp/fitassist/
adb push model-q4.gguf /data/local/tmp/fitassist/
adb push *.so /data/local/tmp/fitassist/

adb shell chmod +x /data/local/tmp/fitassist/llama-cli
```

Teste a execução:

```bash
adb shell 'LD_LIBRARY_PATH=/data/local/tmp/fitassist /data/local/tmp/fitassist/llama-cli -m /data/local/tmp/fitassist/model-q4.gguf -p "Say hello in Portuguese." -n 20 --temp 0.0'
```

---

### Passo 4 — Ligar os sensores

Ative os sensores:

1. Garmin com Heart Rate Broadcast;
2. ThinkRider/trainer com potência e cadência;
3. Bluetooth ligado no Android.

---

### Passo 5 — Abrir o aplicativo Android

Compile e instale o app pelo Android Studio ou via `adb`.

O app deve:

1. carregar o dashboard;
2. buscar o contexto do Node-RED;
3. escanear sensores BLE;
4. exibir HR, potência e cadência;
5. calcular a decisão;
6. gerar a mensagem do coach;
7. validar a resposta do SLM;
8. exibir fallback se necessário.

---

## 6. Fluxo lógico do sistema

```text
Garmin Connect
      ↓
Python backend
      ↓
Node-RED local
      ↓
Android app
      ↓
Dashboard + modelo fisiológico
      ↓
Regra determinística
      ↓
SLM local
      ↓
Validador
      ↓
Mensagem segura ao atleta
```

Fluxo em tempo real:

```text
Garmin HR Broadcast ─┐
ThinkRider Power ────┼──→ Android BLE Sensor Manager
ThinkRider Cadence ──┘
                           ↓
                     Modelo fisiológico
                           ↓
                   Decisão determinística
                           ↓
                     SLM verbaliza
                           ↓
                   App valida e exibe
```

---

## 7. Prompt e validação do SLM

O SLM não decide a ação. Ele apenas transforma uma decisão já calculada em uma frase curta.

Exemplo de entrada estruturada:

```json
{
  "body_battery": 25,
  "power_w": 220,
  "heart_rate_bpm": 155,
  "decision": "Reduzir intensidade",
  "reason": "Body Battery abaixo de 30",
  "severity": "alta"
}
```

Prompt usado:

```text
Task: Rewrite the mandatory coaching decision as one short natural Portuguese sentence.

Rules:
- Output only one sentence.
- Keep the same meaning.
- Do not add new advice.
- The action is mandatory.
- Never suggest increasing effort.
- Forbidden words or ideas:
  aumente, aumentar, acelere, acelerar,
  force, forçar, mais forte, suba o ritmo,
  intensifique, ataque.

Mandatory decision:
Action: Reduzir intensidade
Reason: Body Battery abaixo de 30
Severity: alta

Portuguese sentence:
```

Exemplo de resposta esperada:

```text
Reduza a intensidade e priorize recuperação agora.
```

Exemplo de resposta rejeitada:

```text
Aumente a intensidade e priorize recuperação.
```

Nesse caso, o app rejeita a saída porque ela contradiz a ação obrigatória.

Fallback:

```text
Reduza a intensidade agora. Sua energia está caindo.
```

---

## 8. Modelo fisiológico simplificado

O protótipo usa uma estimativa simples de capacidade/Body Battery durante o treino.

Intensidade externa:

```text
p(t) = P30(t) / FTP
```

Resposta fisiológica:

```text
h(t) = [FC(t) - FCrep] / [FClim - FCrep]
```

Stress instantâneo:

```text
S(t) = 0,6 · p(t)^2 + 0,4 · h(t)^2
```

Carga acumulada:

```text
A(k+1) = A(k) + S(k) · Δt / 3600
```

Estimativa de Body Battery:

```text
B_est(k) = max(B_min, B_Garmin - K · A(k))
```

No código atual, alguns parâmetros foram ajustados de forma agressiva para demonstração visual rápida, por exemplo:

```kotlin
val FC_LIMIAR = 65f
val K_FACTOR = 120000f
```

Esses valores são apenas para teste e devem ser calibrados futuramente com dados reais.

---

## 9. Execução com áudio/TTS

A arquitetura proposta para áudio é:

```text
Regra determinística
      ↓
SLM gera frase segura
      ↓
Validador aprova
      ↓
Android TextToSpeech reproduz a mensagem
```

Exemplo em Kotlin:

```kotlin
tts.speak(
    safeMessage,
    TextToSpeech.QUEUE_FLUSH,
    null,
    "fitassist_coach_message"
)
```

Para o protótipo, o `TextToSpeech` nativo do Android é suficiente.

---

## 10. Problemas comuns

### O app não encontra sensores BLE

Verifique:

- Bluetooth ligado;
- permissões concedidas;
- localização ativa;
- Garmin em modo Heart Rate Broadcast;
- trainer ligado;
- sensores próximos ao Android.

---

### O dashboard não mostra Body Battery

Verifique:

- Node-RED rodando;
- script Python executado;
- IP correto no código Android;
- endpoint `/dashboard/current` respondendo.

Teste:

```bash
curl http://IP_DO_NODE_RED:1880/dashboard/current
```

---

### O SLM não executa

Verifique:

- `llama-cli` copiado para `/data/local/tmp/fitassist`;
- permissão de execução aplicada;
- modelo `.gguf` presente;
- bibliotecas `.so` copiadas;
- `LD_LIBRARY_PATH` configurado.

---

### A LLM gera uma resposta errada

Isso é esperado em alguns casos. O sistema deve validar a saída.

Se a resposta contradiz a decisão, o app usa fallback:

```text
A decisão vem das regras. A LLM apenas verbaliza.
```

---

## 11. Entregáveis esperados

A entrega deve conter:

```text
Relatorio_Final_FitAssist.docx ou .pdf
Codigo/
  README.md
  app-android/
  backend/
  node-red/
  models/
  docs/
video-demonstracao/
```

O arquivo `README.md` deve estar na raiz da pasta `Codigo`.

---

## 12. Observação sobre segurança

O FitAssist é um protótipo acadêmico. As recomendações geradas não substituem avaliação médica, orientação profissional ou percepção subjetiva do atleta.

O princípio de segurança adotado é:

```text
A regra/modelo decide.
A LLM/SLM apenas verbaliza.
O app valida.
Se houver contradição, usa fallback.
```
