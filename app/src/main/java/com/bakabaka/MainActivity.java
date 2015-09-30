package com.bakabaka;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.DialogFragment;
import android.app.AlertDialog;
import android.app.Dialog;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.EditText;
import android.widget.Button;
import android.view.View;
import java.io.IOException;



public class MainActivity extends Activity 
{

	private static NfcAdapter mAdapter;

	byte[] keys = hexString2Byte("F3B1BF94E9D1");

	private String[][] mTechList;
	private IntentFilter[] mIntentFilters;
	private PendingIntent mPendingIntent;
	private Tag mTag;
	private String mData;
	private String valueStr;
	private int mSector = 14;
	private int mBlock1 = 1;
	private int mBlock2 = 2;

 	@Override
 	public void onCreate(Bundle savedInstanceState)
 	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mAdapter = NfcAdapter.getDefaultAdapter(this);

		mTechList = new String[][] {new String[] { android.nfc.tech.MifareClassic.class.getName() }};
		mIntentFilters = new IntentFilter[] { new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED), };
		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		warningDialog dialog = new warningDialog();
		dialog.setCancelable(false);
		dialog.show(getFragmentManager(), "");

		getNfcStatus();
	}

	@Override
	public void onNewIntent(Intent intent)
	{
		setIntent(intent);
		Button btn1 = (Button)findViewById(R.id.read_card);
		btn1.setClickable(true);
		Button btn2 = (Button)findViewById(R.id.write_card);
		btn2.setClickable(true);
		mTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		return;
	}

	public void getNfcStatus()
	{
		if (mAdapter == null || !mAdapter.isEnabled())
		{
			Button btn1 = (Button)findViewById(R.id.read_card);
			btn1.setClickable(false);
			Button btn2 = (Button)findViewById(R.id.write_card);
			btn2.setClickable(false);
			nfcStatusDialog dialog2 = new nfcStatusDialog();
			dialog2.setCancelable(false);
			dialog2.show(getFragmentManager(), "");
		}
	}

	public void onResume()
	{
		super.onResume();
		if (mAdapter != null && mAdapter.isEnabled())
		{
			mAdapter.enableForegroundDispatch(this, mPendingIntent, mIntentFilters, mTechList);
			Toast.makeText(this, "ha" , Toast.LENGTH_SHORT).show();

			if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction()))
			{
				Toast.makeText(this, "发现新卡", Toast.LENGTH_SHORT).show();
			}
		}
 	}

	public static String byte2HexString(byte[] bytes)
	{
		String data = "";
		if (bytes != null)
		{
			for (Byte b : bytes)
			{
				data += String.format("%02X", b.intValue() & 0xFF);
			}
		}
		return data;
 	}


 	public static byte[] hexString2Byte(String str)
	{
		int len = str.length();
		byte[] data = new byte[len / 2];
		try
		{
			for (int i = 0; i < len; i += 2)
			{
				data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return data;
 	}

	public void readTag(View view)
	{
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction()))
		{
			Toast.makeText(this, "读卡中。", Toast.LENGTH_SHORT).show();
			processIntent(getIntent());
		}
		else
		{
			Toast.makeText(this, "你确定有卡么？反正读取失败了。", Toast.LENGTH_SHORT).show();
		}
	}


	public static String getTagId(Intent intent)
	{
		String id = null;
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()))
		{
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			id = byte2HexString(tag.getId());
		}
		return id;
	}

	@Override
 	private void processIntent(Intent intent)
	{
		Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);	
		for (String tech : tagFromIntent.getTechList())
		{
			System.out.println(tech);
		}
		boolean verify = false;	
		MifareClassic mfc = MifareClassic.get(tagFromIntent);	
		try
		{	
			String readLog = "";
			mfc.connect();
			int type = mfc.getType();
			if (type == MifareClassic.TYPE_CLASSIC)
			{
				readLog += "卡类型正确\n";
			}
			readLog += "卡号：" + getTagId(intent);
			verify = mfc.authenticateSectorWithKeyB(mSector, keys);
			int bIndex;	
			if (verify)
			{
				bIndex = mfc.sectorToBlock(mSector) + mBlock2;
				byte[] data = mfc.readBlock(bIndex);
				mData = byte2HexString(data);
				//readLog += "Block " + bIndex + " : " + mData + "\n";
				double mon = getMoney(mData) / 100.00;
				readLog += "\n余额：" + mon;
			}
			else
			{
				readLog += "\n读取数据失败，请检查设备是否支持或卡拿对了没。";	
			}
			TextView promt=(TextView)findViewById(R.id.promt);
			promt.setText(readLog);
		}
		catch (Exception e)
		{
			e.printStackTrace();	
		}
	}

	public void writeTag(String mData, int mMode, int intAddMoney)
	{
		MifareClassic mfc = MifareClassic.get(mTag);
		try
		{
			mfc.close();
			mfc.connect();
			boolean verify = false;
			verify = mfc.authenticateSectorWithKeyB(mSector, keys);
			if (verify)
			{
				int block1 = mfc.sectorToBlock(mSector) + mBlock1;
				int block2 = mfc.sectorToBlock(mSector) + mBlock2;
				String dataTemp = willWriteMoney(mData, mMode, intAddMoney) + valueStr;

				Toast.makeText(this, willWriteMoney(mData, mMode, intAddMoney), Toast.LENGTH_SHORT).show();
				mfc.writeBlock(block1, hexString2Byte(dataTemp));
				mfc.writeBlock(block2, hexString2Byte(dataTemp));

				mfc.close();
				Toast.makeText(this, "写入成功", Toast.LENGTH_SHORT).show();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				mfc.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
 	}

	public void writeMoney(View view)
	{

		RadioButton moneyClear = (RadioButton)findViewById(R.id.moneyClear);
		RadioButton moneyIncr = (RadioButton)findViewById(R.id.moneyIncr);
		RadioButton moneyDecr = (RadioButton)findViewById(R.id.moneyDecr);
		RadioButton moneyReplace = (RadioButton)findViewById(R.id.moneyReplace);

		int mMode = 0;

		if (moneyClear.isChecked())
		{
			mMode = 1;
		}
		else if (moneyIncr.isChecked())
		{
			mMode = 3;
		}
		else if (moneyDecr.isChecked())
		{
			mMode = 4;
		}
		else if (moneyReplace.isChecked())
		{
			mMode = 2;
		}

		EditText edit=(EditText)findViewById(R.id.num);
		String num = edit.getText().toString();
		if (!TextUtils.isEmpty(num) && !TextUtils.isEmpty(mData))
		{
			int i = Integer.parseInt(num);
			if (i > 1000)
			{
				Toast.makeText(this, "金额超出1000", Toast.LENGTH_SHORT).show();
			}
			else if (getMoney(mData) > 3000 && mMode == 3)
			{
				Toast.makeText(this, "卡余额尚充足", Toast.LENGTH_SHORT).show();
			}
			else
			{
				writeTag(mData, mMode, i);
			}
		}
		else
		{
			Toast.makeText(this, "数据错误", Toast.LENGTH_SHORT).show();
		}
	}

	public int getMoney(String mData)
	{

		String cardMoney = null;
		String str1 = mData.substring(0, 1);
		String str2 = mData.substring(1, 2);
		String str3 = mData.substring(2, 3);
		String str4 = mData.substring(3, 4);
		valueStr = mData.substring(4, 32);
		cardMoney = str3 + str4 + str1 + str2;
		Integer intCardMoney = Integer.parseInt(cardMoney, 16);
		return intCardMoney;
	}

	public String willWriteMoney(String mData, int mMode, int intAddMoney)
	{

		//1 清零
		//2 覆写
		//3 增值
		//4 减值

		int intWriteMoney;
		String cardMoney = null;
		int intCardMoney = getMoney(mData);

		if (mMode == 1)
		{
			cardMoney = "0000";
		}
		else if (mMode == 2)
		{
			cardMoney = Integer.toHexString(intAddMoney);
		}
		else if (mMode == 3)
		{
			intWriteMoney = intCardMoney + intAddMoney;
			if (intWriteMoney > 3000)
			{
				intWriteMoney = 3000;
			}
			cardMoney = Integer.toHexString(intWriteMoney);
		}
		else if (mMode == 4)
		{
			if (intCardMoney < intAddMoney)
			{
				intAddMoney = intCardMoney;
			}
			intWriteMoney = intCardMoney - intAddMoney;
			cardMoney = Integer.toHexString(intWriteMoney);
		}

		if (cardMoney.length() == 3)
		{
			cardMoney = "0" + cardMoney;
		}
		else if (cardMoney.length() == 2)
		{
			cardMoney = "00" + cardMoney;
		}
		else if (cardMoney.length() == 1)
		{
			cardMoney = "000" + cardMoney;
		}
		String str1 = cardMoney.substring(0, 1);
		String str2 = cardMoney.substring(1, 2);
		String str3 = cardMoney.substring(2, 3);
		String str4 = cardMoney.substring(3, 4);

		cardMoney = str3 + str4 + str1 + str2;

		return cardMoney;
	}
}

class warningDialog extends DialogFragment
{
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("重要警告");
		builder.setMessage("本软件仅供学习交流之用。\n将此软件用于不法行为者后果自负，与本人无关。");
		builder.setNegativeButton("什么玩意", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface p1, int p2)
				{
					getActivity().finish();
				}
			});
		builder.setPositiveButton("我已明白", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface p1, int p2)
				{

				}
			});
		return builder.create();
	}

}

class nfcStatusDialog extends DialogFragment
{
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("NFC错误");
		builder.setMessage("<b>NFC未开启或者设备不支持NFC，或者手机NFC芯片不支持此卡。</b>");
		builder.setPositiveButton("知道了", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface p1, int p2)
				{
					getActivity().finish();
				}
			});
		return builder.create();
	}

}

