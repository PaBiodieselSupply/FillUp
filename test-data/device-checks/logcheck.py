import os,sys,time,re,csv,datetime as dt; sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import ui
from decimal import Decimal, ROUND_HALF_EVEN
D=os.path.join(os.path.dirname(os.path.abspath(__file__)),"..")
def m3(x):
    s=f"{Decimal(x).quantize(Decimal('0.001'),rounding=ROUND_HALF_EVEN):,.3f}"; return "$"+(s[:-1] if s.endswith('0') else s)
def home():
    for _ in range(4):
        t=ui.texts()
        if "Add Centrifuge Batch" in t: return t
        ui.adb("shell","input","keyevent","4"); time.sleep(1.5)
def select(name):
    for _ in range(3):
        t=home(); cur=t[t.index("Vehicle")+1]
        if cur==name: return
        ui.tap(cur); time.sleep(1.5); ui.tap(name); time.sleep(1.5)
def read_log():
    for i in range(12):
        ui.adb("shell","input","swipe","540","500","540","2100","150"); time.sleep(0.3)
    time.sleep(1)
    rows={}; order=[]; cur=None
    for i in range(25):
        t=ui.texts()
        for x in t:
            if re.fullmatch(r"\d{1,2}/\d{1,2}/\d{2}",x):
                cur=rows.setdefault(x,[]);
                if x not in order: order.append(x)
            elif x in ("Date","Odometer","Gallons","MPG"):
                cur=None  # sticky column header: what follows belongs to a row whose date is off screen
            elif cur is not None and x not in cur and x!="Add Fuel":
                cur.append(x)
        before=t[-3:]
        ui.adb("shell","input","swipe","540","1900","540","800","500"); time.sleep(1)
        if ui.texts()[-3:]==before and i>2: break
    return order,rows
sys.stdout.reconfigure(encoding="utf-8")
total=bad=0
for short,name in (("Truck","Test Truck"),("Tractor","Test Tractor"),("Van","Test Van")):
    select(name); ui.tap("Log"); time.sleep(3)
    order,rows=read_log()
    exp=list(csv.reader(open(os.path.join(D,f"FillUp Bio Test - {short}.csv"),encoding="utf-8")))
    print(name,": read",len(order),"rows, expected",len(exp))
    for r in exp:
        d=dt.datetime.strptime(r[0],"%m/%d/%Y %H:%M"); key=f"{d.month}/{d.day}/{d.strftime('%y')}"
        got=rows.get(key,[])
        want=[r[1],r[2]]
        if r[7]: want.append(r[7] if r[4]=="false" else "---")
        want.append("Fuel Type: "+("Waste Oil Fuel" if r[8]=="wvo" else "Pump Diesel"))
        cost=0.0 if r[8]=="wvo" else float(r[5])
        if cost>0: want.append(f"Cost: ${cost:,.2f} ({m3(cost/float(r[2]))} per gallon)")
        if r[6]: want.append("Notes: "+r[6])
        missing=[w for w in want if w not in got]
        # anything cost-like for a waste oil row is a failure (tractor's typed cost must not show)
        stray=[g for g in got if g.startswith("Cost:") and cost==0]
        total+=1
        if missing or stray:
            bad+=1; print("  BAD",key,"missing:",missing,"stray:",stray,"got:",got)
print(f"rows checked {total}, mismatches {bad}")
