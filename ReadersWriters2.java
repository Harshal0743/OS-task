package readerswriters2;

import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class ReadersWriters2 {
    private static final int READER_COUNT = 5;
    private static final int WRITER_COUNT = 3;
    private static final int OPERATIONS_PER_THREAD = 3;

    private static final Semaphore roomEmpty = new Semaphore(1, true);
    private static final ReentrantLock readersMutex = new ReentrantLock(true);

    private static int readerCount = 0;
    private static int sharedValue = 0;

    // ---------- event recording for the HTML visualisation ----------
    private static final List<String> events = new ArrayList<>();
    private static long startTime;

    // role: R or W | type: WAIT, ENTER, EXIT, FINISH
    private static synchronized void log(String role, int id, String type, int value, String message) {
        long t = System.currentTimeMillis() - startTime;
        events.add("[" + t + ",'" + role + "'," + id + ",'" + type + "'," + value + "]");
        if (message != null) {
            System.out.println(message);
        }
    }

    private static void pauseBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Reader implements Runnable {
        private final int id;

        Reader(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                log("R", id, "WAIT", -1, null);
                readersMutex.lock();
                try {
                    readerCount++;
                    if (readerCount == 1) {
                        roomEmpty.acquireUninterruptibly();
                    }
                } finally {
                    readersMutex.unlock();
                }

                try {
                    int v = sharedValue;
                    log("R", id, "ENTER", v, "Reader " + id + " reads value " + v);
                    pauseBriefly();
                } finally {
                    log("R", id, "EXIT", -1, null); // logged before the count is decremented
                    readersMutex.lock();
                    try {
                        readerCount--;
                        if (readerCount == 0) {
                            roomEmpty.release();
                        }
                    } finally {
                        readersMutex.unlock();
                    }
                }
                pauseBriefly();
            }
            log("R", id, "FINISH", -1, null);
        }
    }

    private static class Writer implements Runnable {
        private final int id;

        Writer(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                log("W", id, "WAIT", -1, null);
                roomEmpty.acquireUninterruptibly();
                try {
                    sharedValue++;
                    log("W", id, "ENTER", sharedValue, "Writer " + id + " writes value " + sharedValue);
                    pauseBriefly();
                } finally {
                    log("W", id, "EXIT", -1, null); // logged before releasing
                    roomEmpty.release();
                }
                pauseBriefly();
            }
            log("W", id, "FINISH", -1, null);
        }
    }

    public static void main(String[] args) {
        Thread[] readers = new Thread[READER_COUNT];
        Thread[] writers = new Thread[WRITER_COUNT];

        for (int i = 0; i < READER_COUNT; i++) {
            readers[i] = new Thread(new Reader(i + 1), "Reader-" + (i + 1));
        }
        for (int i = 0; i < WRITER_COUNT; i++) {
            writers[i] = new Thread(new Writer(i + 1), "Writer-" + (i + 1));
        }

        startTime = System.currentTimeMillis();

        for (Thread reader : readers) {
            reader.start();
        }
        for (Thread writer : writers) {
            writer.start();
        }

        try {
            for (Thread reader : readers) {
                reader.join();
            }
            for (Thread writer : writers) {
                writer.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Main thread was interrupted before all workers finished.");
            return;
        }

        System.out.println("Final shared value: " + sharedValue);
        generateHtml();
    }

    // ---------- HTML generation ----------
    private static void generateHtml() {
        long end = System.currentTimeMillis() - startTime + 300;
        String json;
        synchronized (ReadersWriters2.class) {
            json = "[" + String.join(",", events) + "]";
        }
        String html = PAGE.replace("%%EVENTS%%", json)
                          .replace("%%END%%", String.valueOf(end))
                          .replace("%%NR%%", String.valueOf(READER_COUNT))
                          .replace("%%NW%%", String.valueOf(WRITER_COUNT))
                          .replace("%%OPS%%", String.valueOf(OPERATIONS_PER_THREAD));
        try {
            File f = new File("ReadersWriters_Simulation.html");
            Files.write(f.toPath(), html.getBytes(StandardCharsets.UTF_8));
            System.out.println("\nHTML visualisation created: " + f.getAbsolutePath());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(f.toURI());
            }
        } catch (Exception e) {
            System.out.println("Could not create/open HTML file: " + e);
        }
    }

    private static final String PAGE = String.join("\n",
        "<!DOCTYPE html><html><head><meta charset='UTF-8'>",
        "<title>Readers-Writers Simulation</title>",
        "<style>",
        "*{box-sizing:border-box}body{font-family:Segoe UI,Arial,sans-serif;background:radial-gradient(ellipse at 15% 0%,#e7efff,#f5f7fb 52%);margin:0;padding:28px;color:#182338}",
        "h1{font-size:clamp(24px,3vw,34px);letter-spacing:-.5px;margin:0 0 5px}.sub{margin:0 0 20px;color:#61718a;font-size:14px}",
        ".wrap{display:grid;grid-template-columns:minmax(190px,1fr) minmax(280px,1.5fr) minmax(190px,1fr) minmax(260px,1.4fr);gap:14px;margin-bottom:16px;align-items:stretch}",
        ".card{background:rgba(255,255,255,.92);border:1px solid #e1e8f2;border-radius:16px;padding:17px;box-shadow:0 8px 24px #30496b12}",
        ".zone{min-height:90px;width:auto}.room{width:auto;border:2px solid #65c997;background:linear-gradient(145deg,#f3fff8,#fff);transition:box-shadow .3s,border-color .3s}.room.busy{box-shadow:0 0 0 5px #56c98a22,0 10px 26px #36a87520;border-color:#36b878}.room.conflict{border-color:#ef5350;box-shadow:0 0 0 5px #ef535022}",
        ".chip{display:inline-flex;align-items:center;gap:7px;color:#fff;border-radius:999px;padding:8px 12px;margin:5px 4px;font-size:13px;font-weight:700;box-shadow:0 3px 8px #1d2d441c;animation:arrive .32s cubic-bezier(.2,.8,.2,1) both;transition:transform .2s,filter .2s,opacity .2s}.chip:hover{transform:translateY(-3px) scale(1.04);filter:brightness(1.08)}.chip small{font-weight:600;opacity:.8}.chip{animation:arrive .32s cubic-bezier(.2,.8,.2,1) both}",
        "@keyframes arrive{from{opacity:0;transform:translateY(9px) scale(.88)}to{opacity:1;transform:translateY(0) scale(1)}}@keyframes readPulse{from{box-shadow:0 0 0 0 #4c8bf533}to{box-shadow:0 0 0 6px #4c8bf500}}@keyframes writePulse{from{box-shadow:0 0 0 0 #e5484d55}to{box-shadow:0 0 0 8px #e5484d00}}",
        "#val{font-size:clamp(27px,4vw,40px);font-weight:800;text-align:center;margin:12px 0;color:#213451;transition:transform .18s,color .18s}#val.changed{transform:scale(1.12);color:#e5484d}",
        ".ctl{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:16px;background:#fff;padding:12px 14px;border:1px solid #e1e8f2;border-radius:14px;box-shadow:0 4px 14px #30496b0d}",
        "button,select{padding:8px 14px;font-size:14px;font-weight:650;border-radius:9px;border:1px solid #d3deeb;background:#fff;color:#253650;cursor:pointer;transition:transform .15s,background .15s}button:hover,select:hover{background:#f1f6ff;transform:translateY(-1px)}button#play{background:#315fbb;color:white;border-color:#315fbb}button#play:hover{background:#244f9f}",
        "input[type=range]{accent-color:#315fbb;flex:1;min-width:220px}#time{font-variant-numeric:tabular-nums;color:#54657d;font-weight:700;min-width:68px}",
        "#log{height:250px;width:auto;min-width:0;overflow:auto;font-family:Consolas,monospace;font-size:12px}#log div{padding:6px 5px;border-bottom:1px solid #eef1f6;animation:arrive .2s ease-out}#log div:first-child{background:#f2f6fd;border-radius:5px;color:#213451}",
        ".leg span{display:inline-block;margin:8px 12px 0 0;font-size:12px;color:#5d6d83}.dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:5px}",
        "@media(max-width:1050px){.wrap{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:620px){body{padding:14px}.wrap{grid-template-columns:1fr}.zone{min-height:50px}#log{height:180px}}@media(prefers-reduced-motion:reduce){*,*:before,*:after{animation-duration:.01ms!important;transition-duration:.01ms!important;scroll-behavior:auto!important}}",

        "#stat{font-size:13px;color:#60718a;margin-bottom:6px}",
        "#warn{color:#c43e45;font-weight:bold;font-size:13px;min-height:16px}",
        "</style></head><body>",
        "<h1>Readers-Writers Problem - Semaphore Solution</h1>",
        "<p class='sub'>%%NR%% readers, %%NW%% writers, %%OPS%% operations each. Multiple readers may share the room; a writer needs it exclusively. Replay of the actual run.</p>",
        "<div class='ctl'><button id='play'>Pause</button><button id='restart'>Restart</button>",
        "<select id='speed'><option value='0.1'>0.1x</option><option value='0.25' selected>0.25x</option><option value='0.5'>0.5x</option><option value='1'>1x</option></select>",
        "<input type='range' id='seek' min='0' max='%%END%%' value='0'><span id='time'>0.00 s</span></div>",
        "<div class='wrap'>",
        "<div class='card'><h3>WAITING (blocked)</h3><div class='zone' id='wait'></div></div>",
        "<div class='card room' id='room'><h3>SHARED RESOURCE</h3><div id='stat'></div><div id='val'></div><div id='inside' class='zone'></div><div id='warn'></div></div>",
        "<div class='card'><h3>IDLE / FINISHED</h3><div class='zone' id='idle'></div></div>",
        "<div class='card'><h3>Event log</h3><div id='log'></div></div>",
        "</div>",
        "<div class='card'><h3>Timeline per thread</h3><svg id='g' width='720' height='270'></svg>",
        "<div class='leg' id='leg'></div></div>",
        "<script>",
        "var EV=%%EVENTS%%;",
        "var NR=%%NR%%,NW=%%NW%%,OPS=%%OPS%%,END=%%END%%;",
        "var keys=[];",
        "for(var i=1;i<=NR;i++)keys.push('R'+i);",
        "for(var i=1;i<=NW;i++)keys.push('W'+i);",
        "var CC={R:'#4c8bf5',W:'#e5484d'};",
        "var BC={wait:'#f5a623',read:'#4c8bf5',write:'#e5484d'};",
        "var LBL={wait:'Waiting',read:'Reading',write:'Writing'};",
        "var leg=document.getElementById('leg');",
        "for(var kk in BC){leg.innerHTML+='<span><i class=dot style=background:'+BC[kk]+'></i>'+LBL[kk]+'</span>';}",
        "function stateAt(t){",
        " var th={},val=0,log=[];",
        " keys.forEach(function(k){th[k]={st:'idle',ops:0};});",
        " for(var j=0;j<EV.length;j++){var e=EV[j];if(e[0]>t)break;",
        "  var k=e[1]+e[2],ty=e[3];",
        "  if(ty=='WAIT')th[k].st='wait';",
        "  else if(ty=='ENTER'){th[k].st=e[1]=='R'?'read':'write';if(e[1]=='W')val=e[4];}",
        "  else if(ty=='EXIT'){th[k].st='idle';th[k].ops++;}",
        "  else if(ty=='FINISH')th[k].st='done';",
        "  log.push(e);}",
        " return {th:th,val:val,log:log};}",
        "function chip(k,o){return '<span class=chip style=background:'+CC[k.charAt(0)]+(o.st=='done'?';opacity:.45':'')+'>'+k+' <small>'+o.ops+'/'+OPS+'</small></span>';}",
        "function desc(e){var who=(e[1]=='R'?'Reader ':'Writer ')+e[2],ty=e[3];",
        " if(ty=='WAIT')return who+' is waiting';",
        " if(ty=='ENTER')return e[1]=='R'?who+' reads value '+e[4]:who+' writes value '+e[4];",
        " if(ty=='EXIT')return who+' leaves';",
        " return who+' is DONE';}",
        "var logDiv=document.getElementById('log'),shown=-1;",
        "function put(node,html){if(node.dataset.markup!==html){node.dataset.markup=html;node.innerHTML=html;}}",
        "function render(t){",
        " var s=stateAt(t),w='',n='',d='',nR=0,nW=0;",
        " keys.forEach(function(k){var o=s.th[k];",
        "  if(o.st=='wait')w+=chip(k,o);",
        "  else if(o.st=='read'||o.st=='write'){n+=chip(k,o);if(o.st=='read')nR++;else nW++;}",
        "  else d+=chip(k,o);});",
        " var dw=document.getElementById('wait'),di=document.getElementById('inside'),dd=document.getElementById('idle');",
        " put(dw,w);put(di,n);put(dd,d);",
        " var value=document.getElementById('val'),nextValue='sharedValue = '+s.val;if(value.textContent!==nextValue){value.textContent=nextValue;value.classList.remove('changed');void value.offsetWidth;value.classList.add('changed');}",
        " var st=nW>0?'roomEmpty = 0 (held by a WRITER)':(nR>0?'roomEmpty = 0 (held by readers)':'roomEmpty = 1 (free)');",
        " document.getElementById('stat').textContent=st+'  |  readerCount = '+nR;",
        " document.getElementById('warn').textContent=(nW>1||(nW>0&&nR>0))?'MUTUAL EXCLUSION VIOLATED!':'';",
        " if(s.log.length!=shown){shown=s.log.length;var h='';",
        "  for(var i=s.log.length-1;i>=0;i--){var e=s.log[i];h+='<div>['+(e[0]/1000).toFixed(2)+'s] '+desc(e)+'</div>';}",
        "  logDiv.innerHTML=h;}",
        " ph.setAttribute('x1',X(t));ph.setAttribute('x2',X(t));",
        " document.getElementById('time').textContent=(t/1000).toFixed(2)+' s';",
        " document.getElementById('seek').value=t;}",
        "var G=document.getElementById('g'),NS='http://www.w3.org/2000/svg',LM=40,W=710,RH=28,TOP=8;",
        "function X(t){return LM+(W-LM-10)*t/END;}",
        "function el(tag,at,txt){var e=document.createElementNS(NS,tag);for(var k in at)e.setAttribute(k,at[k]);if(txt)e.textContent=txt;G.appendChild(e);return e;}",
        "keys.forEach(function(k,i){",
        " el('text',{x:6,y:TOP+i*RH+19,'font-size':12,'font-weight':'bold',fill:CC[k.charAt(0)]},k);",
        " el('line',{x1:LM,x2:W-10,y1:TOP+(i+1)*RH,y2:TOP+(i+1)*RH,stroke:'#eee'});});",
        "var step=Math.max(100,Math.ceil(END/8/100)*100);",
        "for(var tt=0;tt<=END;tt+=step){el('line',{x1:X(tt),x2:X(tt),y1:TOP,y2:TOP+keys.length*RH,stroke:'#ddd'});",
        " el('text',{x:X(tt),y:TOP+keys.length*RH+16,'font-size':11,'text-anchor':'middle',fill:'#666'},(tt/1000).toFixed(1)+'s');}",
        "function bar(k,s,e,kind){var i=keys.indexOf(k);",
        " el('rect',{x:X(s),y:TOP+i*RH+4,width:Math.max(2,X(e)-X(s)),height:RH-8,fill:BC[kind],rx:3});}",
        "var open={};",
        "EV.forEach(function(e){var k=e[1]+e[2],ty=e[3],o=open[k];",
        " if(ty=='WAIT')open[k]={s:e[0],kind:'wait'};",
        " else if(ty=='ENTER'){if(o)bar(k,o.s,e[0],'wait');open[k]={s:e[0],kind:e[1]=='R'?'read':'write'};}",
        " else if(ty=='EXIT'){if(o)bar(k,o.s,e[0],o.kind);delete open[k];}});",
        "var ph=el('line',{y1:0,y2:TOP+keys.length*RH,stroke:'#000','stroke-width':1.5});",
        "var cur=0,playing=true,speed=0.25,last=performance.now();",
        "var btn=document.getElementById('play');",
        "function tick(ts){var dt=ts-last;last=ts;",
        " if(playing){cur+=dt*speed;if(cur>=END){cur=END;playing=false;btn.textContent='Play';}}",
        " render(cur);requestAnimationFrame(tick);}",
        "btn.onclick=function(){if(cur>=END)cur=0;playing=!playing;btn.textContent=playing?'Pause':'Play';};",
        "document.getElementById('restart').onclick=function(){cur=0;playing=true;btn.textContent='Pause';};",
        "document.getElementById('speed').onchange=function(){speed=parseFloat(this.value);};",
        "document.getElementById('seek').oninput=function(){cur=parseFloat(this.value);};",
        "requestAnimationFrame(tick);",
        "</script></body></html>");
}