package diningphilophosers2;

import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;


public class DiningPhilosophersMonitor {
    static final int N = 4;
    static final int ROUNDS = 3;
    private static final Object monitor = new Object();
    private static final int THINKING = 0, HUNGRY = 1, EATING = 2;
    private static final int[] state = new int[N];

    // ---------- event recording for the HTML visualisation ----------
    static final List<String> events = new ArrayList<>();
    static long startTime;

    // type: THINK, HUNGRY, PICK, EAT, DONE_EAT, PUT, LEAVE, FINISH
    static synchronized void log(int id, String type, int k, String message) {
        long t = System.currentTimeMillis() - startTime;
        events.add("[" + t + "," + id + ",'" + type + "'," + k + "]");
        System.out.println(message);
    }

    private static int leftOf(int id) { return (id + N - 1) % N; }
    private static int rightOf(int id) { return (id + 1) % N; }

    // Called only while holding the monitor.
    private static void test(int id) {
        if (state[id] == HUNGRY
                && state[leftOf(id)] != EATING
                && state[rightOf(id)] != EATING) {
            state[id] = EATING;
            int left = id;
            int right = rightOf(id);
            log(id, "PICK", left, "Philosopher " + id + " picked up chopstick " + left);
            log(id, "PICK", right, "Philosopher " + id + " picked up chopstick " + right);
            log(id, "EAT", -1, "Philosopher " + id + " is EATING");
            monitor.notifyAll();
        }
    }

    private static void takeChopsticks(int id) throws InterruptedException {
        synchronized (monitor) {
            state[id] = HUNGRY;
            log(id, "HUNGRY", -1, "Philosopher " + id + " is HUNGRY");
            test(id);
            while (state[id] != EATING) {
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    state[id] = THINKING;
                    test(leftOf(id));
                    test(rightOf(id));
                    monitor.notifyAll();
                    throw e;
                }
            }
        }
    }

    private static void putChopsticks(int id) {
        synchronized (monitor) {
            log(id, "DONE_EAT", -1, "Philosopher " + id + " finished eating");
            state[id] = THINKING;
            int left = id;
            int right = rightOf(id);
            log(id, "PUT", left, "Philosopher " + id + " put down chopstick " + left);
            log(id, "PUT", right, "Philosopher " + id + " put down chopstick " + right);
            log(id, "LEAVE", -1, "Philosopher " + id + " left the table");
            test(leftOf(id));
            test(rightOf(id));
            monitor.notifyAll();
        }
    }

    static class Philosopher extends Thread {
        private final int id;
        Philosopher(int id) { this.id = id; }

        @Override public void run() {
            try {
                for (int round = 1; round <= ROUNDS; round++) {
                    think();
                    takeChopsticks(id);
                    try {
                        eat();
                    } finally {
                        putChopsticks(id);
                    }
                }
                log(id, "FINISH", -1, "Philosopher " + id + " is DONE");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Philosopher " + id + " was interrupted.");
            }
        }

        private void think() throws InterruptedException {
            log(id, "THINK", -1, "Philosopher " + id + " is THINKING");
            Thread.sleep((long) (Math.random() * 1000));
        }

        private void eat() throws InterruptedException {
            Thread.sleep((long) (Math.random() * 1000));
        }
    }
    public static void main(String[] args) throws Exception {
        startTime = System.currentTimeMillis();
        Philosopher[] ph = new Philosopher[N];
        for (int i = 0; i < N; i++) {
            ph[i] = new Philosopher(i);
            ph[i].start();
        }
        for (int i = 0; i < N; i++) {
            ph[i].join();
        }

        generateHtml();
    }

    // ---------- HTML generation ----------
    static void generateHtml() {
        long end = System.currentTimeMillis() - startTime + 500;
        String json;
        synchronized (DiningPhilosophersMonitor.class) {
            json = "[" + String.join(",", events) + "]";
        }
        String html = PAGE.replace("%%EVENTS%%", json)
                          .replace("%%END%%", String.valueOf(end))
                          .replace("%%N%%", String.valueOf(N))
                          .replace("%%ROUNDS%%", String.valueOf(ROUNDS));
        try {
            File f = new File("DiningPhilosophers_Monitor_Simulation.html");
            Files.write(f.toPath(), html.getBytes(StandardCharsets.UTF_8));
            System.out.println("\nHTML visualisation created: " + f.getAbsolutePath());
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(f.toURI());
            }
        } catch (Exception e) {
            System.out.println("Could not create/open HTML file: " + e);
        }
    }

    static final String PAGE = String.join("\n",
        "<!DOCTYPE html><html><head><meta charset='UTF-8'>",
        "<title>Dining Philosophers Simulation</title>",
        "<style>",
        "body{font-family:Segoe UI,Arial,sans-serif;background:#f4f6fa;margin:0;padding:20px;color:#222}",
        "h1{font-size:22px;margin:0 0 4px} p.sub{margin:0 0 14px;color:#555;font-size:13px}",
        ".wrap{display:flex;flex-wrap:wrap;gap:20px}",
        ".card{background:#fff;border-radius:10px;padding:14px;box-shadow:0 1px 4px rgba(0,0,0,.15)}",
        "#log{height:330px;width:360px;overflow:auto;font-family:Consolas,monospace;font-size:12px}",
        "#log div{padding:2px 0;border-bottom:1px solid #eee}",
        ".ctl{margin-top:10px;display:flex;align-items:center;gap:10px;flex-wrap:wrap}",
        "button,select{padding:6px 12px;font-size:14px;border-radius:6px;border:1px solid #888;background:#fff;cursor:pointer}",
        "input[type=range]{flex:1;min-width:200px}",
        ".leg span{display:inline-block;margin-right:12px;font-size:12px}",
        ".dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:4px}",
        "</style></head><body>",
        "<h1>Dining Philosophers - Monitor Solution</h1>",
        "<p class='sub'>%%N%% philosophers share the table. A philosopher eats only when neither neighbor is eating. Monitor synchronization using wait() and notifyAll(). Replay of the actual run.</p>",
        "<div class='wrap'>",
        "<div class='card'><svg id='svg' width='560' height='560' viewBox='0 0 560 560'></svg>",
        "<div class='leg' id='leg'></div>",
        "<div class='ctl'><button id='play'>Pause</button><button id='restart'>Restart</button>",
        "<select id='speed'><option value='0.5'>0.5x</option><option value='1' selected>1x</option><option value='2'>2x</option><option value='4'>4x</option></select>",
        "<input type='range' id='seek' min='0' max='%%END%%' value='0'><span id='time'>0.00 s</span></div></div>",
        "<div class='card'><b>Event log</b><div id='log'></div></div>",
        "</div>",
        "<script>",
        "var EVENTS=%%EVENTS%%;",
        "var N=%%N%%,ROUNDS=%%ROUNDS%%,END=%%END%%,MAXROOM=Math.floor(N/2);",
        "var cx=280,cy=280;",
        "var COL={idle:'#9aa0a6',THINK:'#4c8bf5',HUNGRY:'#f5a623',EAT:'#34a853',DONE:'#6b7280'};",
        "var NAME={idle:'not started',THINK:'THINKING',HUNGRY:'HUNGRY (waiting)',EAT:'EATING',DONE:'FINISHED'};",
        "var svg=document.getElementById('svg'),NS='http://www.w3.org/2000/svg';",
        "function ang(i){return (i*360/N-90)*Math.PI/180;}",
        "function el(tag,at){var e=document.createElementNS(NS,tag);for(var k in at)e.setAttribute(k,at[k]);svg.appendChild(e);return e;}",
        "var leg=document.getElementById('leg');",
        "['THINK','HUNGRY','EAT','DONE'].forEach(function(k){leg.innerHTML+='<span><i class=dot style=background:'+COL[k]+'></i>'+NAME[k]+'</span>';});",
        "el('circle',{cx:cx,cy:cy,r:95,fill:'#e9dcc3',stroke:'#b39b6b','stroke-width':3});",
        "var centre=el('text',{x:cx,y:cy+5,'text-anchor':'middle','font-size':16,fill:'#555'});",
        "var circ=[],sub=[],stk=[];",
        "for(var i=0;i<N;i++){",
        " var a=ang(i),px=cx+170*Math.cos(a),py=cy+170*Math.sin(a);",
        " circ[i]=el('circle',{cx:px,cy:py,r:32,fill:COL.idle,stroke:'#333','stroke-width':2});",
        " var l=el('text',{x:px,y:py+5,'text-anchor':'middle','font-size':15,fill:'#fff','font-weight':'bold'});l.textContent='P'+i;",
        " sub[i]=el('text',{x:cx+226*Math.cos(a),y:cy+226*Math.sin(a)+4,'text-anchor':'middle','font-size':12,fill:'#333'});",
        "}",
        "for(var i=0;i<N;i++){stk[i]=el('line',{'stroke-width':6,'stroke-linecap':'round'});}",
        "var TXT={THINK:'is THINKING',HUNGRY:'is HUNGRY',PICK:'picks up chopstick ',EAT:'starts EATING',DONE_EAT:'finished eating',PUT:'puts down chopstick ',LEAVE:'stops eating',FINISH:'is DONE'};",
        "function stateAt(t){",
        " var ph=[],st=[],meals=[],room=0,log=[];",
        " for(var i=0;i<N;i++){ph[i]='idle';st[i]=-1;meals[i]=0;}",
        " for(var j=0;j<EVENTS.length;j++){var e=EVENTS[j];if(e[0]>t)break;",
        "  var p=e[1],ty=e[2],k=e[3];",
        "  if(ty=='THINK')ph[p]='THINK';else if(ty=='HUNGRY')ph[p]='HUNGRY';",
        "  else if(ty=='ENTER'){ph[p]='ENTER';room++;}",
        "  else if(ty=='PICK')st[k]=p;else if(ty=='EAT'){ph[p]='EAT';room++;}",
        "  else if(ty=='DONE_EAT')meals[p]++;else if(ty=='PUT')st[k]=-1;",
        "  else if(ty=='LEAVE')room--;else if(ty=='FINISH')ph[p]='DONE';",
        "  log.push(e);}",
        " return {ph:ph,st:st,room:room,meals:meals,log:log};}",
        "var logDiv=document.getElementById('log'),shown=-1;",
        "function render(t){",
        " var s=stateAt(t);",
        " for(var i=0;i<N;i++){circ[i].setAttribute('fill',COL[s.ph[i]]);sub[i].textContent=NAME[s.ph[i]]+' | meals '+s.meals[i]+'/'+ROUNDS;}",
        " for(var k=0;k<N;k++){var h=s.st[k],ra,r1,r2,c;",
        "  if(h<0){ra=ang(k)-Math.PI/N;r1=55;r2=110;c='#bbb';}",
        "  else{ra=ang(h)+(k==h?-0.25:0.25);r1=100;r2=138;c=COL[s.ph[h]];}",
        "  stk[k].setAttribute('x1',cx+r1*Math.cos(ra));stk[k].setAttribute('y1',cy+r1*Math.sin(ra));",
        "  stk[k].setAttribute('x2',cx+r2*Math.cos(ra));stk[k].setAttribute('y2',cy+r2*Math.sin(ra));",
        "  stk[k].setAttribute('stroke',c);}",
        " centre.textContent='Eating '+s.room+'/'+MAXROOM;",
        " if(s.log.length!=shown){shown=s.log.length;var h2='';",
        "  for(var i=s.log.length-1;i>=0;i--){var e=s.log[i];",
        "   h2+='<div>['+(e[0]/1000).toFixed(2)+'s] Philosopher '+e[1]+' '+TXT[e[2]]+(e[3]>=0?e[3]:'')+'</div>';}",
        "  logDiv.innerHTML=h2;}",
        " document.getElementById('time').textContent=(t/1000).toFixed(2)+' s';",
        " document.getElementById('seek').value=t;}",
        "var cur=0,playing=true,speed=1,last=performance.now();",
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